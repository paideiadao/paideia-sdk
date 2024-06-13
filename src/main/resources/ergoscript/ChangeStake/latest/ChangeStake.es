/** 
 * This contract is a companion contract to the main stake contract.
 * It ensures the stake is changed correctly following the rules.
 *
 * @param imPaideiaDaoKey Token ID of the dao key
 *
 * @return
 */
@contract def changeStake(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;
    #import lib/tokenExists/1.0.0/tokenExists.es;
    #import lib/stakeRecordStake/1.0.0/stakeRecordStake.es;
    #import lib/stakeRecordProfits/1.0.0/stakeRecordProfits.es;

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
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateTree: AvlTree  = stakeState.R4[Coll[AvlTree]].get(0)
    val stakeStateR5: Coll[Long] = stakeState.R5[Coll[Long]].get
    val nextEmission: Long       = stakeStateR5(0)
    val totalStaked: Long        = stakeStateR5(1)
    val stakers: Long            = stakeStateR5(2)
    val voted: Long              = stakeStateR5(3)
    val votedTotal: Long         = stakeStateR5(4)
    val profit: Coll[Long]       = stakeStateR5.slice(5,stakeStateR5.size)
    val stakeStateR6: Coll[Coll[Long]] = 
        stakeState.R6[Coll[Coll[Long]]].get

    val stakeStateR7: Coll[(AvlTree,AvlTree)] = 
        stakeState.R7[Coll[(AvlTree,AvlTree)]].get

    val stakeStateR8: Coll[Long] = stakeState.R8[Coll[Long]].get
    val participationTree: AvlTree     = stakeState.R4[Coll[AvlTree]].get(1)

    val stakeStateTreeO: AvlTree  = stakeStateO.R4[Coll[AvlTree]].get(0)
    val stakeStateOR5: Coll[Long] = stakeStateO.R5[Coll[Long]].get
    val nextEmissionO: Long       = stakeStateOR5(0)
    val totalStakedO: Long        = stakeStateOR5(1)
    val stakersO: Long            = stakeStateOR5(2)
    val votedO: Long              = stakeStateOR5(3)
    val votedTotalO: Long         = stakeStateOR5(4)
    val profitO: Coll[Long]       = stakeStateOR5.slice(5,stakeStateOR5.size)
    val stakeStateOR6: Coll[Coll[Long]] = 
        stakeStateO.R6[Coll[Coll[Long]]].get

    val stakeStateOR7: Coll[(AvlTree,AvlTree)] = 
        stakeStateO.R7[Coll[(AvlTree,AvlTree)]].get

    val stakeStateOR8: Coll[Long] = stakeStateO.R8[Coll[Long]].get
    val participationTreeO: AvlTree     = stakeStateO.R4[Coll[AvlTree]].get(1)

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

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
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
        stakeStateTree.get(stakeOperations(0)._1, proof).get

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
        totalStakedO - totalStaked

    val noPartialUnstake: Boolean = newStakeAmount >= currentStakeAmount

    val singleStakeOp: Boolean = stakeOperations.size == 1

    val correctNewState: Boolean = 
        stakeStateTree.update(stakeOperations, proof).get.digest == 
        stakeStateTreeO.digest

    val noAddedOrNegativeProfit: Boolean = 
        combinedProfit.slice(1,combinedProfit.size).forall{
            (p: (Long, Long)) => p._1 >= p._2 && p._2 >= 0L
        }
    
    val correctErgProfit: Boolean = currentProfits(1) - newProfits(1) == 
        stakeState.value - stakeStateO.value

    val unchangedRegisters: Boolean = allOf(Coll(
        participationTreeO == participationTree,
        profitO == profit,
        nextEmissionO == nextEmission,
        stakersO == stakers,
        votedO == voted,
        votedTotalO == votedTotal,
        stakeStateOR6 == stakeStateR6,
        stakeStateOR7 == stakeStateR7,
        stakeStateOR8 == stakeStateR8
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