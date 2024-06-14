/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def stakeSnapshot(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/emptyDigest/1.0.0/emptyDigest.es;
    #import lib/stakeState/1.0.0/stakeState.es;

    /**
     *
     *  StakeSnapshot
     *
     *  Companion contract to the main stake contract. Handles the logic for
     *  ensuring a snapshot is done correctly
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val imPaideiaContractsStakingSnapShot: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT

    val imPaideiaStakingEmissionAmount: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_EMISSION_AMOUNT

    val imPaideiaStakingEmissionDelay: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_EMISSION_DELAY

    val imPaideiaStakingCycleLength: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_CYCLELENGTH

    val imPaideiaStakingPureParticipationWeight: Coll[Byte] =
        _IM_PAIDEIA_STAKING_WEIGHT_PURE_PARTICIPATION

    val imPaideiaStakingParticipationWeight: Coll[Byte] =
        _IM_PAIDEIA_STAKING_WEIGHT_PARTICIPATION

    val imPaideiaDaoTokenId: Coll[Byte] = _IM_PAIDEIA_DAO_TOKEN_ID

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = INPUTS(0)

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
    val snapshotO: Box   = OUTPUTS(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    // val configTree: AvlTree = config.R4[AvlTree].get

    // val stakeStateTree: AvlTree    = stakeState.R4[Coll[AvlTree]].get(0)
    // val participationTree: AvlTree = stakeState.R4[Coll[AvlTree]].get(1)

    // val stakeStateR5: Coll[Long]    = stakeState.R5[Coll[Long]].get
    // val nextSnapshot: Long          = stakeStateR5(0)
    // val totalStaked: Long           = stakeStateR5(1)
    // val stakers: Long               = stakeStateR5(2)
    // val voted: Long                 = stakeStateR5(3)
    // val votedTotal: Long            = stakeStateR5(4)

    // val stakeStateR6: Coll[Coll[Long]] = 
    //     stakeState.R6[Coll[Coll[Long]]].get

    // val snapshotsStaked: Coll[Long]     = stakeStateR6(0)
    // val snapshotsVoted: Coll[Long]      = stakeStateR6(1)
    // val snapshotsVotedTotal: Coll[Long] = stakeStateR6(2)
    // val snapshotsPPWeight: Coll[Long]   = stakeStateR6(3)
    // val snapshotsPWeight: Coll[Long]    = stakeStateR6(4)

    // val snapshotsTree: Coll[(AvlTree, AvlTree)] = 
    //     stakeState.R7[Coll[(AvlTree, AvlTree)]].get

    // val snapshotsProfit: Coll[Coll[Long]] = stakeState.R8[Coll[Coll[Long]]].get

    // val stakeStateOTree: AvlTree    = stakeStateO.R4[Coll[AvlTree]].get(0)
    // val participationTreeO: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(1)

    // val stakeStateOR5: Coll[Long]      = stakeStateO.R5[Coll[Long]].get
    // val nextSnapshotO: Long            = stakeStateOR5(0)
    // val totalStakedO: Long             = stakeStateOR5(1)
    // val stakersO: Long                 = stakeStateOR5(2)
    // val votedO: Long                   = stakeStateOR5(3)
    // val votedTotalO: Long              = stakeStateOR5(4)

    // val stakeStateOR6: Coll[Coll[Long]] = 
    //     stakeStateO.R6[Coll[Coll[Long]]].get

    // val newSnapshotsStaked: Coll[Long]     = stakeStateOR6(0)
    // val newSnapshotsVoted: Coll[Long]      = stakeStateOR6(1)
    // val newSnapshotsVotedTotal: Coll[Long] = stakeStateOR6(2)
    // val newSnapshotsPPWeight: Coll[Long]   = stakeStateOR6(3)
    // val newSnapshotsPWeight: Coll[Long]    = stakeStateOR6(4)

    // val newSnapshotsTrees: Coll[(AvlTree, AvlTree)] = 
    //     stakeStateO.R7[Coll[(AvlTree, AvlTree)]].get

    // val newSnapshotsProfit: Coll[Long] = 
    //     stakeStateO.R8[Coll[Long]].get

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

    val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
        Coll(
            imPaideiaStakeStateTokenId,
            imPaideiaContractsStakingSnapShot,
            imPaideiaStakingEmissionAmount,
            imPaideiaStakingEmissionDelay,
            imPaideiaStakingCycleLength,
            imPaideiaStakingPureParticipationWeight,
            imPaideiaStakingParticipationWeight,
            imPaideiaDaoTokenId
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte]    = bytearrayToTokenId(configValues(0))
    val snapshotContractHash: Coll[Byte] = bytearrayToContractHash(configValues(1))

    val emissionAmount: Long = bytearrayToLongClamped((configValues(2),(1L,(999999999999999L,1L))))

    val emissionDelay: Int = 
        bytearrayToLongClamped((configValues(3),(1L,(10L,2L)))).toInt

    val cycleLength: Long = bytearrayToLongClamped((configValues(4),(3600000L,(999999999999999L,86400000L))))

    val pureParticipationWeight: Byte = 
        if (configValues(5).isDefined)
            configValues(5).get(1)
        else
            0.toByte

    val participationWeight: Byte = 
        if (configValues(6).isDefined)
            configValues(6).get(1)
        else
            0.toByte

    val daoTokenId: Coll[Byte] = bytearrayToTokenId(configValues(7))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val cappedPPWeight: Byte = max(
        0.toByte,
        min(
            100.toByte, 
            pureParticipationWeight))

    val cappedPWeight: Byte = max(
        0.toByte,
        min(
            100.toByte - cappedPPWeight, 
            participationWeight))

    val newSnapshotIndex: Int = snapshotStaked(stakeStateO).size - 1
        
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctStakeStateTokens: Boolean = stakeState.tokens.zip(stakeStateO.tokens).forall{
        (tt: ((Coll[Byte], Long), (Coll[Byte], Long))) => 
            if (tt._1._1 == daoTokenId) 
                tt._2._1 == tt._1._1 && tt._1._2 + emissionAmount == tt._2._2 
            else 
                tt._2._1 == tt._1._1 && tt._1._2 == tt._2._2
    }

    val correctStakeState: Boolean = allOf(Coll(
        stakeState.tokens(0)._1 == stakeStateTokenId,
        totalStaked(stakeStateO)              == totalStaked(stakeState),
        stakers(stakeStateO)                  == stakers(stakeState),
        votedThisCycle(stakeStateO)           == 0L,
        votesCastThisCycle(stakeStateO)       == 0L,
        stakeTree(stakeStateO).digest         == stakeTree(stakeState).digest,
        participationTree(stakeStateO).digest == emptyDigest
    ))

    val correctNewSnapshot: Boolean = allOf(Coll(
        snapshotStaked(stakeStateO)(newSnapshotIndex) == totalStaked(stakeState),
        snapshotVoted(stakeStateO)(newSnapshotIndex) == votedThisCycle(stakeState),
        snapshotVotesCast(stakeStateO)(newSnapshotIndex) == votesCastThisCycle(stakeState),
        snapshotPureParticipationWeight(stakeStateO)(newSnapshotIndex) == cappedPPWeight,
        snapshotParticipationWeight(stakeStateO)(newSnapshotIndex) == cappedPWeight,
        snapshotTrees(stakeStateO)(newSnapshotIndex)._1.digest == 
            stakeTree(stakeState).digest,
        snapshotTrees(stakeStateO)(newSnapshotIndex)._2.digest == 
            participationTree(stakeState).digest,
        snapshotProfit(stakeStateO)(0) == emissionAmount + profit(stakeState)(0)
    ))

    val correctProfitAddedToSnapshot: Boolean = 
        snapshotProfit(stakeStateO).slice(1,profit(stakeState).size).indices.forall{
            (i: Int) => snapshotProfit(stakeStateO)(i+1) == profit(stakeState)(i+1)}
        

    // When a staker gets rewarded for the staking period his entry gets 
    // removed from the snapshot. An empty snapshot is proof of having handled 
    // all staker rewards for that period.
    val correctHistoryShift = allOf(Coll( 
        snapshotTrees(stakeState)(0)._1.digest == emptyDigest,
        snapshotTrees(stakeStateO).slice(0,emissionDelay-1) == 
            snapshotTrees(stakeState).slice(1,emissionDelay),
        snapshotVoted(stakeStateO).slice(0,emissionDelay-1) == 
            snapshotVoted(stakeState).slice(1,emissionDelay),
        snapshotVotesCast(stakeStateO).slice(0,emissionDelay-1) == 
            snapshotVotesCast(stakeState).slice(1,emissionDelay),
        snapshotPureParticipationWeight(stakeStateO).slice(0,emissionDelay-1) == 
            snapshotPureParticipationWeight(stakeState).slice(1,emissionDelay),
        snapshotParticipationWeight(stakeStateO).slice(0,emissionDelay-1) == 
            snapshotParticipationWeight(stakeState).slice(1,emissionDelay),
        snapshotStaked(stakeStateO).slice(0,emissionDelay-1) == 
            snapshotStaked(stakeState).slice(1,emissionDelay)
    ))

    val profitReset: Boolean = profit(stakeStateO).indices.forall{(p: Int) => profit(stakeStateO)(p) == snapshotProfit(stakeStateO)(p)}

    val correctSize: Boolean = allOf(Coll(
        snapshotTrees(stakeStateO).size == emissionDelay,
        snapshotStaked(stakeStateO).size == emissionDelay,
        snapshotPureParticipationWeight(stakeStateO).size == emissionDelay,
        snapshotParticipationWeight(stakeStateO).size == emissionDelay,
        snapshotVoted(stakeStateO).size == emissionDelay,
        snapshotVotesCast(stakeStateO).size == emissionDelay
    ))

    val correctNextShapshot: Boolean = 
        nextEmission(stakeStateO) == nextEmission(stakeState) + cycleLength

    val correctTime: Boolean = nextEmission(stakeState) <= CONTEXT.preHeader.timestamp

    val correctConfig: Boolean = config.tokens(0)._1 == imPaideiaDaoKey

    val selfOutput: Boolean = allOf(Coll(
        blake2b256(snapshotO.propositionBytes) == snapshotContractHash,
        snapshotO.value >= SELF.value
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        selfOutput,
        correctStakeState,
        correctStakeStateTokens,
        correctNewSnapshot,
        correctHistoryShift,
        correctProfitAddedToSnapshot,
        correctSize,
        profitReset,
        correctNextShapshot,
        correctTime,
        correctConfig
    )))
}