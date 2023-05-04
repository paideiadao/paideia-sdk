{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val stakeInfoOffset = 8

    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_STAKING_VOTE,
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID,
        _IM_PAIDEIA_DAO_PROPOSAL_TOKEN_ID
    ),configProof)

    val stakeVoteContractSignature = configValues(0).get
    val stakingStateTokenId = configValues(1).get
    val proposalTokenId = configValues(2).get.slice(6,38)

    val stakingStateInput = INPUTS(0)

    val proposalInput = INPUTS(2)

    val castVoteInput = INPUTS(3)

    val voteKey = castVoteInput.tokens(0)._1

    val correctStakingState = stakingStateInput.tokens(0)._1 == stakingStateTokenId.slice(6,38)

    val correctProposal = proposalInput.tokens(0)._1 == proposalTokenId

    val votes = proposalInput.R6[AvlTree].get
    val currentVoteProof = getVar[Coll[Byte]](1).get
    val currentVote = votes.get(voteKey, currentVoteProof)

    val newVoteValues = castVoteInput.R5[Coll[Byte]].get.indices.slice(0,castVoteInput.R5[Coll[Byte]].get.size/8).map{
        (i: Int) =>
        byteArrayToLong(castVoteInput.R5[Coll[Byte]].get.slice(i*8,(i+1)*8))
    }
    val newVoteCount = newVoteValues.fold(0L, {(z: Long, v: Long) => z + v})

    val stakeState = stakingStateInput.R4[Coll[AvlTree]].get(0)
    val stakingStateOutput = OUTPUTS(0)
    val voteOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](2).get
    val proof   = getVar[Coll[Byte]](3).get

    val userOutput = OUTPUTS(2)
    val keyInOutput = userOutput.tokens.getOrElse(0,OUTPUTS(0).tokens(0))._1 == voteOperations(0)._1

    val currentStakeState = stakeState.get(voteOperations(0)._1, proof).get
    val currentLockedUntil = byteArrayToLong(currentStakeState.slice(0,8))
    val currentVoted = byteArrayToLong(currentStakeState.slice(8,16))
    val currentVotedTotal = byteArrayToLong(currentStakeState.slice(16,24))

    val newLockedUntil = if (proposalInput.R5[Coll[Long]].get(0) > currentLockedUntil) proposalInput.R5[Coll[Long]].get(0) else currentLockedUntil
    val newVotedTotal = if (currentVote.isDefined) {
        val currentVoteValues = currentVote.get.indices.slice(0,currentVote.get.size/8).map{
            (i: Int) =>
            byteArrayToLong(currentVote.get.slice(i*8,(i+1)*8))
        }
        val currentVoteCount = currentVoteValues.fold(0L, {(z: Long, v: Long) => z + v})
        currentVotedTotal - currentVoteCount + newVoteCount
    } else {
        currentVotedTotal + newVoteCount
    }
    val newVoted = if (currentVote.isDefined) {
        currentVoted
    } else {
        currentVoted + 1
    }

    val updateProof = getVar[Coll[Byte]](4).get
    val updatedState = stakeState.update(Coll((voteOperations(0)._1, 
        longToByteArray(newLockedUntil).append(
            longToByteArray(newVoted)
        ).append(
            longToByteArray(newVotedTotal)
        ).append(
            currentStakeState.slice(24,currentStakeState.size)
        )
    )), updateProof).get

    val correctStakeOutput = allOf(Coll(
        stakingStateOutput.R4[Coll[AvlTree]].get(0).digest == updatedState.digest
    ))

    val voteOutput = OUTPUTS(1)
    val selfOutput = allOf(Coll(
        blake2b256(voteOutput.propositionBytes) == stakeVoteContractSignature.slice(1,33),
        voteOutput.value >= SELF.value
    ))

    sigmaProp(allOf(Coll(
        correctProposal,
        selfOutput
    )))
}