/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def proposalBasic(imPaideiaDaoKey: Coll[Byte], paideiaDaoKey: Coll[Byte], paideiaTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/proposal/1.0.0/proposal.es;
    #import lib/tokenExists/1.0.0/tokenExists.es;
    #import lib/stakeState/1.0.0/stakeState.es;

    /**
     *
     *  CastVote
     *
     *  This contract ensures the is added correctly to the proposal tally and
     *  the stake key is returned to the user.
     *
     */

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

    val imPaideiaStakingStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKENID

    val stakeInfoOffset: Int = 8

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val proposalBasic: Box = SELF

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val config: Box = CONTEXT.dataInputs(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val proposalBasicR4: Coll[Int] = proposalBasic.R4[Coll[Int]].get
    val proposalIndex: Int         = proposalBasicR4(0)
    val passed: Int                = proposalBasicR4(1)

    val proposalBasicR5: Coll[Long] = proposalBasic.R5[Coll[Long]].get
    val endTime: Long               = proposalBasicR5(0)
    val voted: Long                 = proposalBasicR5(1)

    val votes: Coll[Long] = proposalBasicR5.slice(2,proposalBasicR5.size)

    val votesTree: AvlTree = proposalBasic.R6[AvlTree].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues = configTree(config).getMany(
        Coll(
            imPaideiaDaoQuorum,
            imPaideiaDaoThreshold,
            imPaideiaStakingStateTokenId
        ),
        configProof
    )

    val quorumNeeded: Long    = bytearrayToLongClamped((configValues(0),(1L,(999L,500L))))
    val thresholdNeeded: Long = bytearrayToLongClamped((configValues(1),(1L,(999L,500L))))

    val stakeStateTokenId: Coll[Byte] = bytearrayToTokenId(configValues(2))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val proposalBasicIndex = INPUTS.indexOf(proposalBasic, -1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Transaction dependent logic                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val validTransaction: Boolean = 
    if (proposalBasicIndex == 0 && pPassedOption(proposalBasic) == -1) {

        /**
        * EvaluateProposal Transaction
        * Checks that the end time has passed and evaluates which option has won
        * the vote
        */

        ///////////////////////////////////////////////////////////////////////
        // Data Inputs                                                       //
        ///////////////////////////////////////////////////////////////////////

        val stakeState: Box    = CONTEXT.dataInputs(1)
        val paideiaConfig: Box = CONTEXT.dataInputs(2)

        ///////////////////////////////////////////////////////////////////////
        // Outputs                                                           //
        ///////////////////////////////////////////////////////////////////////

        val proposalBasicO: Box = OUTPUTS(0)
        val splitProfitO: Box   = OUTPUTS(1)

        ///////////////////////////////////////////////////////////////////////
        // Registers                                                         //
        ///////////////////////////////////////////////////////////////////////

        val proposalBasicOR4: Coll[Int]  = proposalBasicO.R4[Coll[Int]].get
        val proposalIndexO: Int          = proposalBasicOR4(0)
        val passedOutput: Int            = proposalBasicOR4(1)
        val proposalBasicOR5: Coll[Long] = proposalBasicO.R5[Coll[Long]].get
        val votesTreeO: AvlTree          = proposalBasicO.R6[AvlTree].get

        ///////////////////////////////////////////////////////////////////////
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](1).get
        val winningVote: (Int,Long)        = getVar[(Int,Long)](4).get

        ///////////////////////////////////////////////////////////////////////
        // AVL Tree value extraction                                         //
        ///////////////////////////////////////////////////////////////////////

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
        // Intermediate calculations                                         //
        ///////////////////////////////////////////////////////////////////////

        val passedOption: Int = 
            if (pVoted(proposalBasic) > (totalStaked(stakeState)*quorumNeeded/1000) && winningVote._2 > (pVoted(proposalBasic)*thresholdNeeded/1000)) 
                winningVote._1
            else 
                -2

        val padTokens: Long = min(proposalBasic.tokens(1)._2,padFee)

        ///////////////////////////////////////////////////////////////////////
        // Simple conditions                                                 //
        ///////////////////////////////////////////////////////////////////////

        val correctstakeState: Boolean = 
            stakeState.tokens(0)._1 == stakeStateTokenId

        val paideiaCorrectConfig: Boolean = 
            paideiaConfig.tokens(0)._1 == paideiaDaoKey

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
            proposalBasicO.tokens(0)        == proposalBasic.tokens(0),
            pIndex(proposalBasicO)          == pIndex(proposalBasic),
            pVoted(proposalBasicO)          == pVoted(proposalBasic),
            pVotes(proposalBasicO)          == pVotes(proposalBasic),
            pVoteTree(proposalBasicO)       == pVoteTree(proposalBasic),
            pPassedOption(proposalBasicO)   == passedOption
        ))

        val correctSplitProfitOut: Boolean = allOf(Coll(
            blake2b256(splitProfitO.propositionBytes) == splitProfitContractHash,
            splitProfitO.value >= 1000000L,
            splitProfitO.tokens(0)._1 == paideiaTokenId,
            splitProfitO.tokens(0)._2 >= padTokens
        ))

        val passedEnd: Boolean = CONTEXT.preHeader.timestamp > pEndTime(proposalBasic)

        ///////////////////////////////////////////////////////////////////////
        // Transaction validity                                              //
        ///////////////////////////////////////////////////////////////////////

        allOf(Coll(
            correctstakeState,
            paideiaCorrectConfig,
            correctWinningVote,
            correctOut,
            passedEnd,
            correctSplitProfitOut
        ))

    } else {

        /**
        * Cast Vote Transaction
        * Casts a vote on the proposal ensuring the avl tree is updated and the vote
        * power does not exceed staked amount
        */

        ///////////////////////////////////////////////////////////////////////
        // Inputs                                                            //
        ///////////////////////////////////////////////////////////////////////

        val stakeState: Box = INPUTS(0)

        ///////////////////////////////////////////////////////////////////////
        // Outputs                                                           //
        ///////////////////////////////////////////////////////////////////////

        val proposalBasicO: Box = OUTPUTS(2)

        ///////////////////////////////////////////////////////////////////////
        // Registers                                                         //
        ///////////////////////////////////////////////////////////////////////

        val proposalBasicOR4: Coll[Int] = proposalBasicO.R4[Coll[Int]].get

        val proposalBasicOR5: Coll[Long] = proposalBasicO.R5[Coll[Long]].get
        val endTimeO: Long               = proposalBasicOR5(0)
        val votedO: Long                 = proposalBasicOR5(1)

        val votesO: Coll[Long] = proposalBasicOR5.slice(2,proposalBasicOR5.size)

        val votesTreeO: AvlTree = proposalBasicO.R6[AvlTree].get

        val stakeStateTree: AvlTree = stakeState.R4[Coll[AvlTree]].get(0)

        ///////////////////////////////////////////////////////////////////////
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val currentVoteProof: Coll[Byte] = getVar[Coll[Byte]](1).get
        val newVoteProof: Coll[Byte]     = getVar[Coll[Byte]](2).get
        val stakeProof: Coll[Byte]       = getVar[Coll[Byte]](3).get
        val voteCast: Coll[Byte]         = getVar[Coll[Byte]](5).get
        val voteKey: Coll[Byte]          = getVar[Coll[Byte]](6).get
        
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

        val correctStakeState: Boolean = 
            stakeState.tokens(0)._1 == stakeStateTokenId

        val voteHappened: Boolean = pVotes(proposalBasicO) != pVotes(proposalBasic)

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
                votedO == voted - oldVoteCount + newVoteCount,
                votesO == votes.zip(changedVoteValues).map{
                    (kv: (Long,Long)) => 
                        kv._1+kv._2
                }
            ))
        } else {
            allOf(Coll(
                votedO == voted + newVoteCount,
                votesO == votes.zip(newVoteValues).map{
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
            correctVoteValues,
            voteKeyPresent,
            correctStakeState,
            voteHappened
        ))
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == imPaideiaDaoKey

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctConfig,
        validTransaction
    )))
}