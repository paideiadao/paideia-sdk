/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def unstake(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/box/1.0.0/box.es;
    #import lib/stakeRecord/1.0.0/stakeRecord.es;
    /**
     *
     *  Unstake
     *
     *  Companion contract to the main stake contract. Handles the logic for
     *  unstaking.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val imPaideiaContractsStakingUnstake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = INPUTS(0)
    val unstake: Box    = SELF

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
    val unstakeO: Box    = OUTPUTS(1)

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
            imPaideiaStakeStateTokenId,
            imPaideiaContractsStakingUnstake
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte]   = bytearrayToTokenId(configValues(0))
    val unstakeContractHash: Coll[Byte] = bytearrayToContractHash(configValues(1))

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

    val correctConfig: Boolean = config.tokens(0)._1 == imPaideiaDaoKey

    val correctStakeState: Boolean = allOf(Coll(
        stakeState.tokens(0)._1 == stakeStateTokenId,
        participationTree(stakeStateO) == participationTree(stakeState),
        nextEmission(stakeStateO) == nextEmission(stakeState),
        votedThisCycle(stakeStateO) == votedThisCycle(stakeState),
        votesCastThisCycle(stakeStateO) == votesCastThisCycle(stakeState),
        profit(stakeStateO) == profit(stakeState),
        snapshotValues(stakeStateO) == snapshotValues(stakeState),
        snapshotTrees(stakeStateO) == snapshotTrees(stakeState),
        snapshotProfit(stakeStateO) == snapshotProfit(stakeState)
    ))

    val keyInInput: Boolean = tokenExists((INPUTS, keys(0)))

    val tokensUnstaked: Boolean = allOf(Coll(
        currentStakeAmount == 
            (stakeState.tokens(1)._2 - stakeStateO.tokens(1)._2),
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
        correctConfig,
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