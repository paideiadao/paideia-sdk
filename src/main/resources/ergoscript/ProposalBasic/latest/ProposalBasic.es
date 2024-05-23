/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def proposalBasic(imPaideiaDaoKey: Coll[Byte], paideiaDaoKey: Coll[Byte], paideiaTokenId: Coll[Byte]) = {
    #import lib/bytearrayToLongClamped/1.0.0/bytearrayToLongClamped.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/tokenExists/1.0.0/tokenExists.es;

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

    val configTree: AvlTree = config.R4[AvlTree].get

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

    val configValues = configTree.getMany(
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
    if (proposalBasicIndex == 0 && passed == -1) {

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

        val paideiaConfigTree: AvlTree = paideiaConfig.R4[AvlTree].get

        val totalStaked: Long = stakeState.R5[Coll[Long]].get(1)

        ///////////////////////////////////////////////////////////////////////
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](1).get
        val winningVote: (Int,Long)        = getVar[(Int,Long)](4).get

        ///////////////////////////////////////////////////////////////////////
        // AVL Tree value extraction                                         //
        ///////////////////////////////////////////////////////////////////////

        val paideiaConfigValues = paideiaConfigTree.getMany(
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
            if (voted > (totalStaked*quorumNeeded/1000) && winningVote._2 > (voted*thresholdNeeded/1000)) 
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

        val correctWinningVote: Boolean = votes.indices.forall{
                    (i: Int) =>
                    if (i==winningVote._1) votes(i) == winningVote._2
                    else votes(i) <= winningVote._2
            } && 
            votes.size > winningVote._1 &&
            winningVote._1 >= 0

        val correctOut: Boolean = allOf(Coll(
            proposalBasicO.propositionBytes == proposalBasic.propositionBytes,
            proposalBasicO.value >= proposalBasic.value - 3000000L,
            proposalBasicO.tokens(0) == proposalBasic.tokens(0),
            proposalIndexO == proposalIndex,
            proposalBasicOR5 == proposalBasicR5,
            votesTreeO == votesTree,
            passedOutput == passedOption
        ))

        val correctSplitProfitOut: Boolean = allOf(Coll(
            blake2b256(splitProfitO.propositionBytes) == splitProfitContractHash,
            splitProfitO.value >= 1000000L,
            splitProfitO.tokens(0)._1 == paideiaTokenId,
            splitProfitO.tokens(0)._2 >= padTokens
        ))

        val passedEnd: Boolean = CONTEXT.preHeader.timestamp > endTime

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
            votesTree.get(voteKey,currentVoteProof)

        val newVotesTree: AvlTree = 
            if (currentVote.isDefined) {
                votesTree.update(Coll((voteKey,voteCast)),newVoteProof).get
            } else {
                votesTree.insert(Coll((voteKey,voteCast)),newVoteProof).get
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
            stakeStateTree.get(voteKey, stakeProof).get

        val currentStakeAmount: Long = 
            byteArrayToLong(
                currentStakeState.slice(stakeInfoOffset,stakeInfoOffset+8)
            )

        ///////////////////////////////////////////////////////////////////////
        // Simple conditions                                                 //
        ///////////////////////////////////////////////////////////////////////

        val correctStakeState: Boolean = 
            stakeState.tokens(0)._1 == stakeStateTokenId

        val R5Changed: Boolean = proposalBasicOR5 != proposalBasicR5

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
            voteKeyPresent,
            currentStakeAmount >= newVoteCount,
            proposalBasicO.propositionBytes == proposalBasic.propositionBytes,
            proposalBasicO.value >= proposalBasic.value,
            proposalBasicO.tokens == proposalBasic.tokens,
            proposalBasicOR4 == proposalBasicR4,
            endTimeO == endTime,
            correctVoteValues,
            votesTreeO.digest == newVotesTree.digest,
            correctStakeState,
            R5Changed
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