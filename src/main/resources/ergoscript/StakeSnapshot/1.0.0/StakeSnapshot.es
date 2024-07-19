/** 
 *  Companion contract to the main stake contract. Handles the logic for
 *  ensuring a snapshot is done correctly
 *
 * @param imPaideiaDaoKey Token ID of the dao key
 * @param stakeStateTokenId Token ID of the stake state nft
 *
 * @return
 */
@contract def stakeSnapshot(imPaideiaDaoKey: Coll[Byte], stakeStateTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/emptyDigest/1.0.0/emptyDigest.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/box/1.0.0/box.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////


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

    val stakeState: Box = filterByTokenId((INPUTS, stakeStateTokenId))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val config: Box = filterByTokenId((CONTEXT.dataInputs, imPaideiaDaoKey))(0)

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

    val snapshotContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))

    val emissionAmount: Long = bytearrayToLongClamped((configValues(1),(0L,(999999999999999L,0L))))

    val emissionDelay: Int = 
        bytearrayToLongClamped((configValues(2),(1L,(10L,1L)))).toInt

    val cycleLength: Long = bytearrayToLongClamped((configValues(3),(3600000L,(999999999999999L,86400000L))))

    val pureParticipationWeight: Byte = 
        if (configValues(4).isDefined)
            configValues(4).get(1)
        else
            0.toByte

    val participationWeight: Byte = 
        if (configValues(5).isDefined)
            configValues(5).get(1)
        else
            0.toByte

    val daoTokenId: Coll[Byte] = bytearrayToTokenId(configValues(6))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeStateO: Box = filterByTokenId((OUTPUTS, stakeStateTokenId))(0)
    val snapshotO: Box   = filterByHash((OUTPUTS,snapshotContractHash))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    //Make sure pure participation weight is between 0 and 100
    val cappedPPWeight: Byte = max(
        0.toByte,
        min(
            100.toByte, 
            pureParticipationWeight))

    //Make sure participation weight is between 0 and (100-pure participation weight)
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

    //Tokens should be the same except for staking emissions
    val correctStakeStateTokens: Boolean = stakeState.tokens.zip(stakeStateO.tokens).forall{
        (tt: ((Coll[Byte], Long), (Coll[Byte], Long))) => 
            if (tt._1._1 == daoTokenId) 
                tt._2._1 == tt._1._1 && tt._1._2 + emissionAmount == tt._2._2 
            else 
                tt._2 == tt._1
    }

    val correctStakeState: Boolean = allOf(Coll(
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

    //A new staking epoch starts with 0 profit
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

    val selfOutput: Boolean = snapshotO.value >= SELF.value

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
        correctTime
    )))
}