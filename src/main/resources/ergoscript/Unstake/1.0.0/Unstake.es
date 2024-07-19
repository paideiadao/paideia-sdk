/** 
 *  Companion contract to the main stake contract. Handles the logic for
 *  unstaking.
 *
 * @param imPaideiaDaoKey Token ID of the dao key
 * @param stakeStateTokenId Token ID of the stake state nft
 *
 * @return
 */
@contract def unstake(imPaideiaDaoKey: Coll[Byte], stakeStateTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/box/1.0.0/box.es;
    #import lib/stakeRecord/1.0.0/stakeRecord.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsStakingUnstake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = filterByTokenId((INPUTS, stakeStateTokenId))(0)
    val unstake: Box    = SELF

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
    val keys: Coll[Coll[Byte]]  = getVar[Coll[Coll[Byte]]](1).get
    val proof: Coll[Byte]       = getVar[Coll[Byte]](2).get
    val removeProof: Coll[Byte] = getVar[Coll[Byte]](3).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
        Coll(
            imPaideiaContractsStakingUnstake
        ),
        configProof
    )

    val unstakeContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeStateO: Box = filterByTokenId((OUTPUTS, stakeStateTokenId))(0)
    val unstakeO: Box    = filterByHash((OUTPUTS, unstakeContractHash))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeRecord: Coll[Byte] = stakeTree(stakeState).get(keys(0), proof).get

    val currentStakeAmount: Long = stakeRecordStake(stakeRecord)

    val currentProfits: Coll[Long] = stakeRecordProfits(stakeRecord)
        
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctStakeState: Boolean = allOf(Coll(
        participationTree(stakeStateO)  == participationTree(stakeState),
        nextEmission(stakeStateO)       == nextEmission(stakeState),
        votedThisCycle(stakeStateO)     == votedThisCycle(stakeState),
        votesCastThisCycle(stakeStateO) == votesCastThisCycle(stakeState),
        profit(stakeStateO)             == profit(stakeState),
        snapshotValues(stakeStateO)     == snapshotValues(stakeState),
        snapshotTrees(stakeStateO)      == snapshotTrees(stakeState),
        snapshotProfit(stakeStateO)     == snapshotProfit(stakeState)
    ))

    val keyInInput: Boolean = tokenExists((INPUTS, keys(0)))

    val tokensUnstaked: Boolean = allOf(Coll(
        currentStakeAmount == 
            (govToken(stakeState)._2 - govToken(stakeStateO)._2),
        currentStakeAmount == totalStaked(stakeState) - totalStaked(stakeStateO)
    ))

    val correctErgProfit: Boolean = 
        currentProfits(0) == stakeState.value - stakeStateO.value

    val singleStakeOp: Boolean = keys.size == 1

    val correctStakersCount: Boolean = stakers(stakeState) - 1L == stakers(stakeStateO)

    val correctNewState: Boolean = 
        stakeTree(stakeState).remove(keys, removeProof).get.digest == 
            stakeTree(stakeStateO).digest

    val unlocked: Boolean = CONTEXT.preHeader.timestamp > stakeRecordLockedUntil(stakeRecord)
        
    val selfOutput: Boolean = allOf(Coll(
        blake2b256(unstakeO.propositionBytes) == unstakeContractHash,
        unstakeO.value >= unstake.value
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctStakeState,
        keyInInput,
        tokensUnstaked,
        correctErgProfit,
        singleStakeOp,
        correctNewState,
        selfOutput,
        correctStakersCount,
        unlocked
    )))
}