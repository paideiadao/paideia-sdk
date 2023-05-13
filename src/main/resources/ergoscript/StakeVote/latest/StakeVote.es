{

    /**
     *
     *  StakeVote
     *
     *  Companion contract to the main stake contract. Ensures the users'
     *  participation in the governance process is stored correctly
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

    val imPaideiaContractsStakingVote: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_VOTE

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val imPaideiaDaoProposalTokenId: Coll[Byte] = 
        _IM_PAIDEIA_DAO_PROPOSAL_TOKEN_ID

    val stakeInfoOffset: Int = 8

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = INPUTS(0)
    val vote: Box       = SELF
    val proposal: Box   = INPUTS(2)
    val castVote: Box   = INPUTS(3)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val config: Box = CONTEXT.dataInputs(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeStateO: Box = OUTPUTS(0)
    val voteO: Box       = OUTPUTS(1)
    val userO: Box       = OUTPUTS(3)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val proposalEndTime: Long = proposal.R5[Coll[Long]].get(0)
    val votes: AvlTree        = proposal.R6[AvlTree].get

    val voteCasted: Coll[Byte] = castVote.R5[Coll[Byte]].get

    val stakeStateTree: AvlTree    = stakeState.R4[Coll[AvlTree]].get(0)
    val participationTree: AvlTree = stakeState.R4[Coll[AvlTree]].get(1)
    val stakeStateR5: Coll[Long]   = stakeState.R5[Coll[Long]].get
    val nextEmission: Long         = stakeStateR5(0)
    val totalStaked: Long          = stakeStateR5(1)
    val stakers: Long              = stakeStateR5(2)
    val voted: Long                = stakeStateR5(3)
    val votedTotal: Long           = stakeStateR5(4)
    val profit: Long               = stakeStateR5.slice(5, stakeStateR5.size)
    val stakeStateR6: Coll[Coll[Long]] = 
        stakeState.R6[Coll[Coll[Long]]].get

    val stakeStateR7: Coll[(AvlTree,AvlTree)] = 
        stakeState.R7[Coll[(AvlTree,AvlTree)]].get

    val stakeStateR8: Coll[Coll[Long]] = 
        stakeState.R8[Coll[Coll[Long]]].get

    val stakeStateOTree: AvlTree    = stakeStateO.R4[Coll[AvlTree]].get(0)
    val participationTreeO: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(1)
    val stakeStateOR5: Coll[Long]   = stakeStateO.R5[Coll[Long]].get
    val nextEmissionO: Long         = stakeStateOR5(0)
    val totalStakedO: Long          = stakeStateOR5(1)
    val stakersO: Long              = stakeStateOR5(2)
    val votedO: Long                = stakeStateOR5(3)
    val votedTotalO: Long           = stakeStateOR5(4)
    val profitO: Long               = stakeStateOR5.slice(5, stakeStateOR5.size)
    val stakeStateOR6: Coll[Coll[Long]] = 
        stakeStateO.R6[Coll[Coll[Long]]].get

    val stakeStateOR7: Coll[(AvlTree,AvlTree)] = 
        stakeStateO.R7[Coll[(AvlTree,AvlTree)]].get

    val stakeStateOR8: Coll[Coll[Long]] = 
        stakeStateO.R8[Coll[Coll[Long]]].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte]              = getVar[Coll[Byte]](0).get
    val currentVoteProof: Coll[Byte]         = getVar[Coll[Byte]](1).get
    val stakeProof: Coll[Byte]               = getVar[Coll[Byte]](2).get
    val updateStakeProof: Coll[Byte]         = getVar[Coll[Byte]](3).get
    val participationProof: Coll[Byte]       = getVar[Coll[Byte]](4).get
    val updateParticipationProof: Coll[Byte] = getVar[Coll[Byte]](5).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            imPaideiaContractsStakingVote,
            imPaideiaStakeStateTokenId,
            imPaideiaDaoProposalTokenId
        ),
        configProof
    )

    val stakeVoteContractHash: Coll[Byte] = configValues(0).get.slice(1,33)
    val stakeStateTokenId: Coll[Byte]     = configValues(1).get.slice(6,38)
    val proposalTokenId: Coll[Byte]       = configValues(2).get.slice(6,38)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeKey: Coll[Byte] = castVote.tokens(0)._1

    val currentVote: Option[Coll[Byte]] = votes.get(stakeKey, currentVoteProof)

    val newVoteValues: Coll[Long] = 
        voteCasted.indices.slice(0,voteCasted.size/8).map{
            (i: Int) =>
            byteArrayToLong(voteCasted.slice(i*8,(i+1)*8))
        }

    val newVoteCount: Long = newVoteValues.fold(0L, {
        (z: Long, v: Long) => z + v
    })

    val currentStakeState: Coll[Byte] = 
        stakeStateTree.get(stakeKey, stakeProof).get

    val currentParticipation: Option[Coll[Byte]] = 
        participationTree.get(stakeKey, participationProof)

    val currentLockedUntil: Long = byteArrayToLong(currentStakeState.slice(0,8))

    val currentVoted: Long = 
        if (currentParticipation.isDefined) 
            byteArrayToLong(currentParticipation.get.slice(0,8))
        else
            0L

    val currentVotedTotal: Long = 
        if (currentParticipation.isDefined) 
            byteArrayToLong(currentParticipation.get.slice(8,16))
        else
            0L

    val newLockedUntil: Long = max(proposalEndTime,currentLockedUntil)

    val newVotedTotal: Long = if (currentVote.isDefined) {
        val currentVoteValues: Coll[Long] = currentVote.get.indices
            .slice(0,currentVote.get.size/8).map{
                (i: Int) =>
                byteArrayToLong(currentVote.get.slice(i*8,(i+1)*8))
            }
        val currentVoteCount: Long = currentVoteValues.fold(0L, {
            (z: Long, v: Long) => z + v
        })
        currentVotedTotal - currentVoteCount + newVoteCount
    } else {
        currentVotedTotal + newVoteCount
    }

    val newVoted: Long = if (currentVote.isDefined) {
        currentVoted
    } else {
        currentVoted + 1L
    }

    val updatedStakeState: AvlTree = stakeStateTree.update(
        Coll((stakeKey, 
            longToByteArray(newLockedUntil).append(
                currentStakeState.slice(8,currentStakeState.size)
            )
        )), updateStakeProof).get

    val updatedParticipationState: AvlTree = 
        if (currentParticipation.isDefined)
            participationTree.update(
                Coll((stakeKey, 
                    longToByteArray(newVoted).append(
                        longToByteArray(newVotedTotal)
                    )
                )), updateParticipationProof).get
        else
            participationTree.insert(
                Coll((stakeKey, 
                    longToByteArray(newVoted).append(
                        longToByteArray(newVotedTotal)
                    )
                )), updateParticipationProof).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId

    val correctProposal: Boolean = proposal.tokens(0)._1 == proposalTokenId

    val keyInOutput: Boolean = userO.tokens(0)._1 == stakeKey

    val correctStakeOutput: Boolean = allOf(Coll(
        stakeStateO.value == stakeState.value,
        stakeStateO.tokens == stakeState.tokens,
        stakeStateOTree.digest == updatedStakeState.digest,
        participationTreeO.digest == updatedParticipationState.digest,
        nextEmissionO == nextEmission,
        totalStakedO == totalStaked,
        stakersO == stakers,
        votedO == voted + newVoted - currentVoted,
        votedTotalO == votedTotal + newVotedTotal - currentVotedTotal,
        profitO == profit,
        stakeStateOR6 == stakeStateR6,
        stakeStateOR7 == stakeStateR7,
        stakeStateOR8 == stakeStateR8
    ))

    val selfOutput: Boolean = allOf(Coll(
        blake2b256(voteO.propositionBytes) == stakeVoteContractHash,
        voteO.value >= vote.value
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctProposal,
        selfOutput,
        keyInOutput,
        correctStakeOutput,
        correctStakeState,
        correctConfig
    )))
}