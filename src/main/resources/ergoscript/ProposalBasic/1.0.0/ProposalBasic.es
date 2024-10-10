/** 
 * This contract ensures votes are cast fairly and evaluation is done 
 * according to the rules as defined by the framework and the dao config
 *
 * @param imPaideiaDaoKey Token ID of the dao key
 * @param paideiaDaoKey Token ID of the Paideia DAO key 
 * @param paideiaTokenId Token ID of the paideia token
 * @param stakeStateTokenId Token ID of the stake state nft
 *
 * @return
 */
@contract def proposalBasic(imPaideiaDaoKey: Coll[Byte], paideiaDaoKey: Coll[Byte], paideiaTokenId: Coll[Byte], stakeStateTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/proposal/1.0.0/proposal.es;
    #import lib/box/1.0.0/box.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/garbageCollect/1.0.0/garbageCollect.es;
    #import lib/txTypes/1.0.0/txTypes.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaDaoQuorum: Coll[Byte]    = _IM_PAIDEIA_DAO_QUORUM
    val imPaideiaDaoThreshold: Coll[Byte] = _IM_PAIDEIA_DAO_THRESHOLD

    val imPaideiaContractsSplitProfit: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT

    val imPaideiaFeesCreateProposalPaideia: Coll[Byte] = 
        _IM_PAIDEIA_FEES_CREATE_PROPOSAL_PAIDEIA

    val stakeInfoOffset: Int = 8

    def validVoteTransaction(txType: Byte): Boolean = {
        if (txType == VOTE) {
        ///////////////////////////////////////////////////////////////////////
        // Inputs                                                            //
        ///////////////////////////////////////////////////////////////////////

        val proposalBasic: Box = SELF
        val stakeState: Box    = filterByTokenId((INPUTS, stakeStateTokenId))(0)

        ///////////////////////////////////////////////////////////////////////
        // Outputs                                                           //
        ///////////////////////////////////////////////////////////////////////

        val proposalBasicO: Box = filterByHash((OUTPUTS,blake2b256(proposalBasic.propositionBytes)))(0)

        ///////////////////////////////////////////////////////////////////////
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val collBytesVars: Coll[Coll[Byte]] = getVar[Coll[Coll[Byte]]](1).get
        val currentVoteProof: Coll[Byte] = collBytesVars(0)
        val newVoteProof: Coll[Byte]     = collBytesVars(1)
        val stakeProof: Coll[Byte]       = collBytesVars(2)
        val voteCast: Coll[Byte]         = collBytesVars(3)
        val voteKey: Coll[Byte]          = collBytesVars(4)
        
        ///////////////////////////////////////////////////////////////////////
        // Intermediate calculations                                         //
        ///////////////////////////////////////////////////////////////////////

        val voteKeyPresent: Boolean = tokenExists((INPUTS, voteKey))

        val currentVote: Option[Coll[Byte]] = 
            pVoteTree(proposalBasic).get(voteKey,currentVoteProof)

        val newVotesTree: AvlTree = 
            if (currentVote.isDefined) {
                pVoteTree(proposalBasic).update(Coll((voteKey,voteCast)),newVoteProof).get
            } else {
                pVoteTree(proposalBasic).insert(Coll((voteKey,voteCast)),newVoteProof).get
            }

        val newVoteValues: Coll[Long] = 
            voteCast.indices.slice(0,voteCast.size/8).map{
                (i: Int) =>
                byteArrayToLong(voteCast.slice(i*8,(i+1)*8))
            }

        val newVoteCount: Long = newVoteValues.fold(0L,{
            (z: Long, v: Long) => z+v
        })

        val currentStakeState: Coll[Byte] = 
            stakeTree(stakeState).get(voteKey, stakeProof).get

        val currentStakeAmount: Long = 
            byteArrayToLong(
                currentStakeState.slice(stakeInfoOffset,stakeInfoOffset+8)
            )

        ///////////////////////////////////////////////////////////////////////
        // Simple conditions                                                 //
        ///////////////////////////////////////////////////////////////////////

        val voteHappened: Boolean = pVotes(proposalBasicO) != pVotes(proposalBasic)

        val notPassedEnd: Boolean = CONTEXT.preHeader.timestamp < pEndTime(proposalBasic)

        val correctVoteValues = if (currentVote.isDefined) {
            val oldVoteValues: Coll[Long] = 
                currentVote.get.indices.slice(0,currentVote.get.size/8).map{
                    (i: Int) =>
                    byteArrayToLong(currentVote.get.slice(i*8,(i+1)*8))
                }
            val oldVoteCount: Long =
                oldVoteValues.fold(0L,{(z: Long, v: Long) => z+v})

            val changedVoteValues: Coll[Long] = 
                oldVoteValues.zip(newVoteValues)
                .map{(kv: (Long,Long)) => kv._2-kv._1}

            allOf(Coll(
                pVoted(proposalBasicO) == pVoted(proposalBasic) - oldVoteCount + newVoteCount,
                pVotes(proposalBasicO) == pVotes(proposalBasic).zip(changedVoteValues).map{
                    (kv: (Long,Long)) => 
                        kv._1+kv._2
                }
            ))
        } else {
            allOf(Coll(
                pVoted(proposalBasicO) == pVoted(proposalBasic) + newVoteCount,
                pVotes(proposalBasicO) == pVotes(proposalBasic).zip(newVoteValues).map{
                    (kv: (Long,Long)) => 
                        kv._1+kv._2
                }
            ))
        } 

        ///////////////////////////////////////////////////////////////////////
        // Transaction validity                                              //
        ///////////////////////////////////////////////////////////////////////  

        allOf(Coll(
            currentStakeAmount               >= newVoteCount,
            proposalBasicO.propositionBytes  == proposalBasic.propositionBytes,
            proposalBasicO.value             >= proposalBasic.value,
            proposalBasicO.tokens            == proposalBasic.tokens,
            pIndex(proposalBasicO)           == pIndex(proposalBasic),
            pPassedOption(proposalBasicO)    == pPassedOption(proposalBasic),
            pEndTime(proposalBasicO)         == pEndTime(proposalBasic),
            pVoteTree(proposalBasicO).digest == newVotesTree.digest,
            notPassedEnd,
            correctVoteValues,
            voteKeyPresent,
            voteHappened
        ))
        } else { false }
    }

    def validEvaluateTransaction(txType: Byte): Boolean = {
        if (txType == EVALUATE) {
        ///////////////////////////////////////////////////////////////////////
        // Inputs                                                            //
        ///////////////////////////////////////////////////////////////////////

        val proposalBasic: Box = SELF

        ///////////////////////////////////////////////////////////////////////
        // Data Inputs                                                       //
        ///////////////////////////////////////////////////////////////////////

        val stakeState: Box    = filterByTokenId((CONTEXT.dataInputs, stakeStateTokenId))(0)
        val paideiaConfig: Box = filterByTokenId((CONTEXT.dataInputs,paideiaDaoKey))(0)
        val config: Box        = filterByTokenId((CONTEXT.dataInputs, imPaideiaDaoKey))(0)

        ///////////////////////////////////////////////////////////////////////
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val configProof: Coll[Byte]        = getVar[Coll[Byte]](1).getOrElse(Coll[Byte]())
        val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](2).getOrElse(Coll[Byte]())
        val winningVote: Option[(Int,Long)] = getVar[(Int,Long)](10).getOrElse((-2,0L))

        ///////////////////////////////////////////////////////////////////////
        // AVL Tree value extraction                                         //
        ///////////////////////////////////////////////////////////////////////

        val configValues = configTree(config).getMany(
            Coll(
                imPaideiaDaoQuorum,
                imPaideiaDaoThreshold
            ),
            configProof
        )

        val quorumNeeded: Long    = bytearrayToLongClamped((configValues(0),(1L,(999L,500L))))
        val thresholdNeeded: Long = bytearrayToLongClamped((configValues(1),(1L,(999L,500L))))

        val paideiaConfigValues = configTree(paideiaConfig).getMany(
            Coll(
                imPaideiaFeesCreateProposalPaideia,
                imPaideiaContractsSplitProfit
            ),
            paideiaConfigProof
        )

        val padFee: Long = byteArrayToLong(paideiaConfigValues(0).get.slice(1,9))

        val splitProfitContractHash: Coll[Byte] = bytearrayToContractHash(paideiaConfigValues(1))

        ///////////////////////////////////////////////////////////////////////
        // Outputs                                                           //
        ///////////////////////////////////////////////////////////////////////

        val proposalBasicO: Box = filterByHash((OUTPUTS, blake2b256(proposalBasic.propositionBytes)))(0)
        val splitProfitO: Box   = filterByHash((OUTPUTS, splitProfitContractHash))(0)

        ///////////////////////////////////////////////////////////////////////
        // Intermediate calculations                                         //
        ///////////////////////////////////////////////////////////////////////

        val passedOption: Int = 
            if (pVoted(proposalBasic) >= (totalStaked(stakeState)*quorumNeeded/1000) && winningVote._2 >= (pVoted(proposalBasic)*thresholdNeeded/1000)) 
                winningVote._1
            else 
                -2

        val padTokens: Long = min(proposalBasic.tokens(1)._2,padFee)

        ///////////////////////////////////////////////////////////////////////
        // Simple conditions                                                 //
        ///////////////////////////////////////////////////////////////////////

        val correctWinningVote: Boolean = pVotes(proposalBasic).indices.forall{
                    (i: Int) =>
                    if (i==winningVote._1) pVotes(proposalBasic)(i) == winningVote._2
                    else pVotes(proposalBasic)(i) <= winningVote._2
            } && 
            pVotes(proposalBasic).size > winningVote._1 &&
            winningVote._1 >= 0

        val correctOut: Boolean = allOf(Coll(
            proposalBasicO.propositionBytes == proposalBasic.propositionBytes,
            proposalBasicO.value            >= proposalBasic.value - 3000000L,
            proposalBasicO.value            >= 2000000L,
            pProposalToken(proposalBasicO)  == pProposalToken(proposalBasic),
            pIndex(proposalBasicO)          == pIndex(proposalBasic),
            pVoted(proposalBasicO)          == pVoted(proposalBasic),
            pVotes(proposalBasicO)          == pVotes(proposalBasic),
            pVoteTree(proposalBasicO)       == pVoteTree(proposalBasic),
            pPassedOption(proposalBasicO)   == passedOption
        ))

        val correctSplitProfitOut: Boolean = allOf(Coll(
            splitProfitO.value >= 1000000L,
            tokensInBoxes((Coll(splitProfitO),paideiaTokenId)) >= padTokens
        ))

        val passedEnd: Boolean = CONTEXT.preHeader.timestamp > pEndTime(proposalBasic)

        val notEvaluatedYet: Boolean = pPassedOption(proposalBasic) == -1

        ///////////////////////////////////////////////////////////////////////
        // Transaction validity                                              //
        ///////////////////////////////////////////////////////////////////////

        allOf(Coll(
            correctWinningVote,
            correctOut,
            passedEnd,
            correctSplitProfitOut,
            notEvaluatedYet
        ))
        } else { false }
    }

    def validGarbageCollectTransaction(txType: Byte): Boolean = {
        if (txType == GARBAGE_COLLECT) {
            garbageCollect(Coll(pProposalToken(SELF)._1))
        } else { false }
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val transactionType: Byte = getVar[Byte](0).get
       
    sigmaProp(anyOf(Coll(
        validGarbageCollectTransaction(transactionType),
        validEvaluateTransaction(transactionType),
        validVoteTransaction(transactionType)
    ))) 
}