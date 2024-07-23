/** 
 *  Companion contract to the main stake contract. Ensures the users'
 *  participation in the governance process is stored correctly
 *
 * @param imPaideiaDaoKey Token ID of the dao key
 * @param stakeStateTokenId Token ID of the stake state nft
 *
 * @return
 */
@contract def stakeVote(imPaideiaDaoKey: Coll[Byte], stakeStateTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/stakeRecord/1.0.0/stakeRecord.es;
    #import lib/box/1.0.0/box.es;
    #import lib/proposal/1.0.0/proposal.es;
    #import lib/stakeState/1.0.0/stakeState.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsStakingVote: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_VOTE

    val imPaideiaDaoProposalTokenId: Coll[Byte] = 
        _IM_PAIDEIA_DAO_PROPOSAL_TOKEN_ID

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = filterByTokenId((INPUTS, stakeStateTokenId))(0)
    val vote: Box       = SELF

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val config: Box = CONTEXT.dataInputs(0)

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
    val newStakeRecord: Coll[Byte]           = getVar[Coll[Byte]](6).get
    val newParticipationRecord: Coll[Byte]   = getVar[Coll[Byte]](7).get
    val castedVote: Coll[Byte]               = getVar[Coll[Byte]](8).get
    val stakeKey: Coll[Byte]                 = getVar[Coll[Byte]](9).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
        Coll(
            imPaideiaContractsStakingVote,
            imPaideiaDaoProposalTokenId
        ),
        configProof
    )

    val stakeVoteContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))
    val proposalTokenId: Coll[Byte]       = bytearrayToTokenId(configValues(1))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeStateO: Box = filterByTokenId((OUTPUTS, stakeStateTokenId))(0)
    val voteO: Box       = filterByHash((OUTPUTS, stakeVoteContractHash))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val proposal: Box = filterByTokenId((INPUTS, proposalTokenId))(0)

    val currentVote: Option[Coll[Byte]] = pVoteTree(proposal).get(stakeKey, currentVoteProof)

    val newVoteValues: Coll[Long] = 
        castedVote.indices.slice(0,castedVote.size/8).map{
            (i: Int) =>
            byteArrayToLong(castedVote.slice(i*8,(i+1)*8))
        }

    val newVoteCount: Long = newVoteValues.fold(0L, {
        (z: Long, v: Long) => z + v
    })

    val currentStakeState: Coll[Byte] = 
        stakeTree(stakeState).get(stakeKey, stakeProof).get

    val currentParticipation: Option[Coll[Byte]] = 
        participationTree(stakeState).get(stakeKey, participationProof)

    val currentLockedUntil: Long = stakeRecordLockedUntil(currentStakeState)

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

    val newLockedUntil: Long = max(pEndTime(proposal),currentLockedUntil)

    val newVotedTotal: Long = if (currentVote.isDefined) {
        val currentVoteValues: Coll[Long] = currentVote.get.indices
            .slice(0,currentVote.get.size/8).map{
                (i: Int) =>
                byteArrayToLong(currentVote.get.slice(i*8,(i+1)*8))
            }
        val currentVoteCount: Long = currentVoteValues.fold(0L, {
            (z: Long, v: Long) => z + v
        })
        max(currentVotedTotal - currentVoteCount + newVoteCount,0L)
    } else {
        currentVotedTotal + newVoteCount
    }

    val newVoted: Long = if (currentVote.isDefined) {
        currentVoted
    } else {
        currentVoted + 1L
    }

    val updatedStakeRecord: Coll[Byte] = 
        longToByteArray(newLockedUntil).append(
            currentStakeState.slice(8,currentStakeState.size)
        )

    val updatedStakeState: AvlTree = stakeTree(stakeState).update(
        Coll((
            stakeKey, 
            updatedStakeRecord
        )), updateStakeProof).get

    val updatedParticipationRecord: Coll[Byte] = 
        longToByteArray(newVoted).append(
            longToByteArray(newVotedTotal)
        )

    val updatedParticipationState: AvlTree = 
        if (currentParticipation.isDefined)
            participationTree(stakeState).update(
                Coll((
                    stakeKey, 
                    updatedParticipationRecord
                )), updateParticipationProof).get
        else
            participationTree(stakeState).insert(
                Coll((
                    stakeKey, 
                    updatedParticipationRecord
                )), updateParticipationProof).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val keyInOutput: Boolean = tokenExists((OUTPUTS, stakeKey))

    val correctStakeOutput: Boolean = allOf(Coll(
        stakeStateO.value == stakeState.value,
        stakeStateO.tokens == stakeState.tokens,
        stakeTree(stakeStateO).digest == updatedStakeState.digest,
        participationTree(stakeStateO).digest == updatedParticipationState.digest,
        nextEmission(stakeStateO) == nextEmission(stakeState),
        totalStaked(stakeStateO) == totalStaked(stakeState),
        stakers(stakeStateO) == stakers(stakeState),
        votedThisCycle(stakeStateO) == votedThisCycle(stakeState) + newVoted - currentVoted,
        votesCastThisCycle(stakeStateO) == votesCastThisCycle(stakeState) + newVotedTotal - currentVotedTotal,
        profit(stakeStateO) == profit(stakeState),
        snapshotValues(stakeStateO) == snapshotValues(stakeState),
        snapshotTrees(stakeStateO) == snapshotTrees(stakeState),
        snapshotProfit(stakeStateO) == snapshotProfit(stakeState)
    ))

    val correctNewStakeRecord: Boolean = newStakeRecord == updatedStakeRecord

    val correctNewParticipationRecord: Boolean = 
        newParticipationRecord == updatedParticipationRecord

    val selfOutput: Boolean = voteO.value >= vote.value

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        selfOutput,
        keyInOutput,
        correctStakeOutput,
        correctNewStakeRecord,
        correctNewParticipationRecord
    )))
}