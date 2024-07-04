/** 
 * This contract is a companion contract to the main stake contract.
 * It ensures the stake is changed correctly following the rules.
 *
 * @param imPaideiaDaoKey Token ID of the dao key
 *
 * @return
 */
@contract def changeStake(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/box/1.0.0/box.es;
    #import lib/stakeRecord/1.0.0/stakeRecord.es;
    #import lib/stakeState/1.0.0/stakeState.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsStakingChangeStake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_CHANGESTAKE

    val imPaideiaStakingStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val changeStake: Box = SELF
    val stakeState: Box  = INPUTS(0)

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

    val stakeStateO: Box  = OUTPUTS(0)
    val changeStakeO: Box = OUTPUTS(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    val stakeOperations: Coll[(Coll[Byte], Coll[Byte])]  = 
        getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get

    val proof: Coll[Byte] = getVar[Coll[Byte]](2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
        Coll(
            imPaideiaContractsStakingChangeStake,
            imPaideiaStakingStateTokenId
        ),
        configProof
    )

    val changeStakeContractSignature: Coll[Byte] = bytearrayToContractHash(configValues(0))
    val stakingStakeTokenId: Coll[Byte]          = bytearrayToTokenId(configValues(1))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////
    
    val stakeRecord: Coll[Byte] = 
        stakeTree(stakeState).get(stakeOperations(0)._1, proof).get

    val currentStakeAmount: Long = stakeRecordStake(stakeRecord)
    val newStakeAmount: Long     = stakeRecordStake(stakeOperations(0)._2)

    val currentProfits: Coll[Long] = 
        Coll(currentStakeAmount) ++ stakeRecordProfits(stakeRecord)

    val newProfits: Coll[Long] = 
        Coll(newStakeAmount) ++ stakeRecordProfits(stakeOperations(0)._2)

    val combinedProfit: Coll[(Long, Long)] = currentProfits.zip(newProfits) 

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfigTokenId: Boolean = config.tokens(0)._1 == imPaideiaDaoKey

    val selfOutput: Boolean = allOf(Coll(
        blake2b256(changeStakeO.propositionBytes) == changeStakeContractSignature,
        changeStakeO.value >= changeStake.value
    ))

    val correctStakeState: Boolean = stakeState.tokens(0)._1 == stakingStakeTokenId

    val keyInOutput: Boolean = tokenExists((OUTPUTS, stakeOperations(0)._1))

    val tokensStaked: Boolean = newStakeAmount - currentStakeAmount == 
        (stakeStateO.tokens(1)._2 - stakeState.tokens(1)._2) && 
        newStakeAmount - currentStakeAmount == 
        totalStaked(stakeStateO) - totalStaked(stakeState)

    val noPartialUnstake: Boolean = newStakeAmount >= currentStakeAmount

    val singleStakeOp: Boolean = stakeOperations.size == 1

    val correctNewState: Boolean = 
        stakeTree(stakeState).update(stakeOperations, proof).get.digest == 
        stakeTree(stakeStateO).digest

    val noAddedOrNegativeProfit: Boolean = 
        combinedProfit.slice(1,combinedProfit.size).forall{
            (p: (Long, Long)) => p._1 >= p._2 && p._2 >= 0L
        }
    
    val correctErgProfit: Boolean = currentProfits(1) - newProfits(1) == 
        stakeState.value - stakeStateO.value

    val unchangedRegisters: Boolean = allOf(Coll(
        participationTree(stakeStateO)  == participationTree(stakeState),
        profit(stakeStateO)             == profit(stakeState),
        nextEmission(stakeStateO)       == nextEmission(stakeState),
        stakers(stakeStateO)            == stakers(stakeState),
        votedThisCycle(stakeStateO)     == votedThisCycle(stakeState),
        votesCastThisCycle(stakeStateO) == votesCastThisCycle(stakeState),
        snapshotValues(stakeStateO)     == snapshotValues(stakeState),
        snapshotTrees(stakeStateO)      == snapshotTrees(stakeState),
        snapshotProfit(stakeStateO)     == snapshotProfit(stakeState)
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        correctStakeState,
        keyInOutput,
        tokensStaked,
        singleStakeOp,
        correctNewState,
        noAddedOrNegativeProfit,
        correctErgProfit,
        selfOutput,
        unchangedRegisters,
        noPartialUnstake
    )))
}