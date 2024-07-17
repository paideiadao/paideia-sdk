/** 
 * This contract is a companion contract to the main stake contract.
 * It ensures the stake is changed correctly following the rules.
 *
 * @param imPaideiaDaoKey Token ID of the dao key
 * @param stakingStakeTokenId Token ID of the stake state NFT
 *
 * @return
 */
@contract def changeStake(imPaideiaDaoKey: Coll[Byte], stakingStakeTokenId: Coll[Byte]) = {
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

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val changeStake: Box = SELF
    val stakeState: Box = filterByTokenId((INPUTS, stakingStakeTokenId))(0)

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
            imPaideiaContractsStakingChangeStake
        ),
        configProof
    )

    val changeStakeContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeStateO: Box  = filterByTokenId((OUTPUTS,stakingStakeTokenId))(0)
    val changeStakeO: Box = filterByHash((OUTPUTS, changeStakeContractHash))(0)

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

    val selfOutput: Boolean = changeStakeO.value >= changeStake.value

    val keyInOutput: Boolean = tokenExists((OUTPUTS, stakeOperations(0)._1))

    val tokensStaked: Boolean = newStakeAmount - currentStakeAmount == 
        (govToken(stakeStateO)._2 - govToken(stakeState)._2) && 
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