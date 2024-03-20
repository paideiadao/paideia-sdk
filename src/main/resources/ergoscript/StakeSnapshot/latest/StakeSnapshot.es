{
    #import lib/bytearrayToLongClamped/1.0.0/bytearrayToLongClamped.es;

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

    val daoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

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

    val imPaideiaStakingProfitTokenIds: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS

    val imPaideiaStakingPureParticipationWeight: Coll[Byte] =
        _IM_PAIDEIA_STAKING_WEIGHT_PURE_PARTICIPATION

    val imPaideiaStakingParticipationWeight: Coll[Byte] =
        _IM_PAIDEIA_STAKING_WEIGHT_PARTICIPATION

    val imPaideiaDaoTokenId: Coll[Byte] = _IM_PAIDEIA_DAO_TOKEN_ID

    val emptyDigest: Coll[Byte] = 
        Coll(78,-58,31,72,91,-104,-21,-121,21,63,124,87,-37,79,94,-51,117,
            85,111,-35,-68,64,59,65,-84,-8,68,31,-34,-114,22,9,0).map{
                (i: Int) => i.toByte
            }

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

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateTree: AvlTree    = stakeState.R4[Coll[AvlTree]].get(0)
    val participationTree: AvlTree = stakeState.R4[Coll[AvlTree]].get(1)

    val stakeStateR5: Coll[Long]    = stakeState.R5[Coll[Long]].get
    val nextSnapshot: Long          = stakeStateR5(0)
    val totalStaked: Long           = stakeStateR5(1)
    val stakers: Long               = stakeStateR5(2)
    val voted: Long                 = stakeStateR5(3)
    val votedTotal: Long            = stakeStateR5(4)

    val stakeStateR6: Coll[Coll[Long]] = 
        stakeState.R6[Coll[Coll[Long]]].get

    val snapshotsStaked: Coll[Long]     = stakeStateR6(0)
    val snapshotsVoted: Coll[Long]      = stakeStateR6(1)
    val snapshotsVotedTotal: Coll[Long] = stakeStateR6(2)
    val snapshotsPPWeight: Coll[Long]   = stakeStateR6(3)
    val snapshotsPWeight: Coll[Long]    = stakeStateR6(4)

    val snapshotsTree: Coll[(AvlTree, AvlTree)] = 
        stakeState.R7[Coll[(AvlTree, AvlTree)]].get

    val snapshotsProfit: Coll[Coll[Long]] = stakeState.R8[Coll[Coll[Long]]].get

    val stakeStateOTree: AvlTree    = stakeStateO.R4[Coll[AvlTree]].get(0)
    val participationTreeO: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(1)

    val stakeStateOR5: Coll[Long]      = stakeStateO.R5[Coll[Long]].get
    val nextSnapshotO: Long            = stakeStateOR5(0)
    val totalStakedO: Long             = stakeStateOR5(1)
    val stakersO: Long                 = stakeStateOR5(2)
    val votedO: Long                   = stakeStateOR5(3)
    val votedTotalO: Long              = stakeStateOR5(4)

    val stakeStateOR6: Coll[Coll[Long]] = 
        stakeStateO.R6[Coll[Coll[Long]]].get

    val newSnapshotsStaked: Coll[Long]     = stakeStateOR6(0)
    val newSnapshotsVoted: Coll[Long]      = stakeStateOR6(1)
    val newSnapshotsVotedTotal: Coll[Long] = stakeStateOR6(2)
    val newSnapshotsPPWeight: Coll[Long]   = stakeStateOR6(3)
    val newSnapshotsPWeight: Coll[Long]    = stakeStateOR6(4)

    val newSnapshotsTrees: Coll[(AvlTree, AvlTree)] = 
        stakeStateO.R7[Coll[(AvlTree, AvlTree)]].get

    val newSnapshotsProfit: Coll[Long] = 
        stakeStateO.R8[Coll[Long]].get

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

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            imPaideiaStakeStateTokenId,
            imPaideiaContractsStakingSnapShot,
            imPaideiaStakingEmissionAmount,
            imPaideiaStakingEmissionDelay,
            imPaideiaStakingCycleLength,
            imPaideiaStakingProfitTokenIds,
            imPaideiaStakingPureParticipationWeight,
            imPaideiaStakingParticipationWeight,
            imPaideiaDaoTokenId
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte]    = configValues(0).get.slice(6,38)
    val snapshotContractHash: Coll[Byte] = configValues(1).get.slice(1,33)

    val emissionAmount: Long = bytearrayToLongClamped((configValues(2),(1L,(999999999999999L,1L))))

    val emissionDelay: Int = 
        bytearrayToLongClamped((configValues(3),(1L,(10L,2L)))).toInt

    val cycleLength: Long = bytearrayToLongClamped((configValues(4),(3600000L,(999999999999999L,86400000L))))

    val profitTokenIds: Coll[Byte] = configValues(5).get

    val pureParticipationWeight: Byte = 
        if (configValues(6).isDefined)
            configValues(6).get(1)
        else
            0.toByte

    val participationWeight: Byte = 
        if (configValues(7).isDefined)
            configValues(7).get(1)
        else
            0.toByte

    val daoTokenId: Coll[Byte] = configValues(8).get.slice(6,38)

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

    val whiteListedTokenIds: Coll[Coll[Byte]] = 
        profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{
            (i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }

    val profit: Coll[Long] = stakeStateR5.slice(5,stakeStateR5.size).append(
        whiteListedTokenIds.slice(
            stakeStateR5.size-3,
            whiteListedTokenIds.size)
        .map{(tokId: Coll[Byte]) => 0L}
    )

    val outputProfit: Coll[Long] = stakeStateOR5.slice(5,stakeStateOR5.size)
        
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
        correctStakeStateTokens,
        totalStakedO == totalStaked,
        stakersO == stakers,
        votedO == 0L,
        votedTotalO == 0L,
        stakeStateOTree.digest == stakeStateTree.digest,
        participationTreeO.digest == emptyDigest
    ))

    val correctNewSnapshot: Boolean = allOf(Coll(
        newSnapshotsStaked(newSnapshotsStaked.size-1) == totalStaked,
        newSnapshotsVoted(newSnapshotsStaked.size-1) == voted,
        newSnapshotsVotedTotal(newSnapshotsStaked.size-1) == votedTotal,
        newSnapshotsPPWeight(newSnapshotsStaked.size-1) == cappedPPWeight,
        newSnapshotsPWeight(newSnapshotsPWeight.size-1) == cappedPWeight,
        newSnapshotsTrees(newSnapshotsTrees.size-1)._1.digest == 
            stakeStateTree.digest,
        newSnapshotsTrees(newSnapshotsTrees.size-1)._2.digest == 
            participationTree.digest,
        newSnapshotsProfit(0) == emissionAmount + profit(0)
    ))

    val correctProfitAddedToSnapshot: Boolean = 
        newSnapshotsProfit.slice(1,profit.size).indices.forall{
            (i: Int) => newSnapshotsProfit(i+1) == profit(i+1)}
        

    // When a staker gets rewarded for the staking period his entry gets 
    // removed from the snapshot. An empty snapshot is proof of having handled 
    // all staker rewards for that period.
    val correctHistoryShift = allOf(Coll( 
        snapshotsTree(0)._1.digest == emptyDigest,
        newSnapshotsTrees.slice(0,emissionDelay-1) == 
            snapshotsTree.slice(1,emissionDelay),
        newSnapshotsVoted.slice(0,emissionDelay-1) == 
            snapshotsVoted.slice(1,emissionDelay),
        newSnapshotsVotedTotal.slice(0,emissionDelay-1) == 
            snapshotsVotedTotal.slice(1,emissionDelay),
        newSnapshotsPPWeight.slice(0,emissionDelay-1) == 
            snapshotsPPWeight.slice(1,emissionDelay),
        newSnapshotsPWeight.slice(0,emissionDelay-1) == 
            snapshotsPWeight.slice(1,emissionDelay),
        newSnapshotsStaked.slice(0,emissionDelay-1) == 
            snapshotsStaked.slice(1,emissionDelay)
    ))

    val profitReset: Boolean = outputProfit.indices.forall{(p: Int) => outputProfit(p) == newSnapshotsProfit(p)}

    val correctSize: Boolean = allOf(Coll(
        newSnapshotsTrees.size == emissionDelay,
        newSnapshotsStaked.size == emissionDelay,
        newSnapshotsPPWeight.size == emissionDelay,
        newSnapshotsPWeight.size == emissionDelay,
        newSnapshotsVoted.size == emissionDelay,
        newSnapshotsVotedTotal.size == emissionDelay
    ))

    val correctNextShapshot: Boolean = 
        nextSnapshotO == nextSnapshot + cycleLength

    val correctTime: Boolean = nextSnapshot <= CONTEXT.preHeader.timestamp

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

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