{

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

    val stakeStateTree: AvlTree = stakeState.R4[Coll[AvlTree]].get(0)

    val stakeStateR5: Coll[Long]    = stakeState.R5[Coll[Long]].get
    val nextSnapshot: Long          = stakeStateR5(0)
    val totalStaked: Long           = stakeStateR5(1)
    val snapshotsStaked: Coll[Long] = stakeState.R6[Coll[Long]].get

    val snapshotsTree: Coll[(AvlTree, AvlTree)] = 
        stakeState.R7[Coll[(AvlTree, AvlTree)]].get

    val snapshotsProfit: Coll[Coll[Long]] = stakeState.R8[Coll[Coll[Long]]].get

    val stakeStateOR5: Coll[Long]      = stakeStateO.R5[Coll[Long]].get
    val nextSnapshotO: Long            = stakeStateOR5(0)
    val newSnapshotsStaked: Coll[Long] = stakeStateO.R6[Coll[Long]].get

    val newSnapshotsTrees: Coll[(AvlTree, AvlTree)] = 
        stakeStateO.R7[Coll[(AvlTree, AvlTree)]].get

    val newSnapshotsProfit: Coll[Coll[Long]] = 
        stakeStateO.R8[Coll[Coll[Long]]].get

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
            imPaideiaStakingProfitTokenIds
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte]    = configValues(0).get.slice(6,38)
    val snapshotContractHash: Coll[Byte] = configValues(1).get.slice(1,33)

    val emissionAmount: Long = byteArrayToLong(configValues(2).get.slice(1,9))

    val emissionDelay: Int = 
        byteArrayToLong(configValues(3).get.slice(1,9)).toInt

    val cycleLength: Long = byteArrayToLong(configValues(4).get.slice(1,9))

    val profitTokenIds: Coll[Byte] = configValues(5).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val whiteListedTokenIds: Coll[Coll[Byte]] = 
        profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{
            (i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }

    val profit: Coll[Long] = stakeStateR5.slice(4,stakeStateR5.size).append(
        whiteListedTokenIds.slice(
            stakeStateR5.size-3,
            whiteListedTokenIds.size)
        .map{(tokId: Coll[Byte]) => 0L}
    )

    val outputProfit: Coll[Long] = stakeStateOR5.slice(4,stakeStateOR5.size)
        
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId

    val correctNewSnapshot: Boolean = allOf(Coll(
        newSnapshotsStaked(newSnapshotsStaked.size-1) == totalStaked,
        newSnapshotsTrees(newSnapshotsTrees.size-1)._1.digest == 
            stakeStateTree.digest,
        newSnapshotsProfit(newSnapshotsProfit.size-1)(0) == 
        min(
            emissionAmount,
            stakeState.tokens(1)._2-totalStaked-profit(0)-1
        )
    ))

    val correctProfitAddedToSnapshot: Boolean = allOf(Coll(
        newSnapshotsProfit(0).slice(1,profit.size).indices.forall{
            (i: Int) => newSnapshotsProfit(0)(i+1) == profit(i+1)},
        newSnapshotsProfit(0)(0) == snapshotsProfit(1)(0) + profit(0)
    ))

    // When a staker gets rewarded for the staking period his entry gets 
    // removed from the snapshot. An empty snapshot is proof of having handled 
    // all staker rewards for that period.
    val correctHistoryShift = allOf(Coll( 
        snapshotsTree(0)._1.digest == emptyDigest,
        newSnapshotsTrees.slice(0,emissionDelay-1) == 
            snapshotsTree.slice(1,emissionDelay),
        newSnapshotsStaked.slice(0,emissionDelay-1).indices.forall{
            (i: Int) => newSnapshotsStaked(i) == snapshotsStaked(i+1)}
    ))

    val profitReset: Boolean = outputProfit.forall{(p: Long) => p==0L}

    val correctSize: Boolean = allOf(Coll(
        newSnapshotsTrees.size == emissionDelay,
        newSnapshotsStaked.size == emissionDelay,
        newSnapshotsProfit.size == emissionDelay
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
        correctSize,
        profitReset,
        correctNextShapshot,
        correctTime,
        correctConfig
    )))
}