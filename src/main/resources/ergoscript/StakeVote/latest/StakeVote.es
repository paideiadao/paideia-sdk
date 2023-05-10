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
    val userO: Box       = OUTPUTS(2)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val proposalEndTime: Long = proposal.R5[Coll[Long]].get(0)
    val votes: AvlTree        = proposal.R6[AvlTree].get

    val voteCasted: Coll[Byte] = castVote.R5[Coll[Byte]].get

    val stakeStateTree: AvlTree = stakeState.R4[Coll[AvlTree]].get(0)

    val stakeStateOTree: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte]      = getVar[Coll[Byte]](0).get
    val currentVoteProof: Coll[Byte] = getVar[Coll[Byte]](1).get

    val voteOperations: Coll[(Coll[Byte], Coll[Byte])] = 
        getVar[Coll[(Coll[Byte], Coll[Byte])]](2).get

    val proof: Coll[Byte]       = getVar[Coll[Byte]](3).get
    val updateProof: Coll[Byte] = getVar[Coll[Byte]](4).get

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

    val voteKey: Coll[Byte] = castVote.tokens(0)._1

    val currentVote: Option[Coll[Byte]] = votes.get(voteKey, currentVoteProof)

    val newVoteValues: Coll[Long] = 
        voteCasted.indices.slice(0,voteCasted.size/8).map{
            (i: Int) =>
            byteArrayToLong(voteCasted.slice(i*8,(i+1)*8))
        }

    val newVoteCount: Long = newVoteValues.fold(0L, {
        (z: Long, v: Long) => z + v
    })

    val currentStakeState: AvlTree = 
        stakeStateTree.get(voteOperations(0)._1, proof).get

    val currentLockedUntil: Long = byteArrayToLong(currentStakeState.slice(0,8))
    val currentVoted: Long = byteArrayToLong(currentStakeState.slice(8,16))
    val currentVotedTotal: Long = byteArrayToLong(currentStakeState.slice(16,24))

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

    val updatedState: AvlTree = stakeStateTree.update(
        Coll((voteOperations(0)._1, 
            longToByteArray(newLockedUntil).append(
                longToByteArray(newVoted)
            ).append(
                longToByteArray(newVotedTotal)
            ).append(
                currentStakeState.slice(24,currentStakeState.size)
            )
        )), updateProof).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId

    val correctProposal: Boolean = proposal.tokens(0)._1 == proposalTokenId

    val keyInOutput: Boolean = userO.tokens
        .getOrElse(0,OUTPUTS(0).tokens(0))._1 == voteOperations(0)._1

    val correctStakeOutput: Boolean = allOf(Coll(
        stakeStateOTree.digest == updatedState.digest
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
        selfOutput
    )))
}