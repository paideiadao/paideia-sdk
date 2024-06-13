/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def unstake(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;
    #import lib/tokensInBoxes/1.0.0/tokensInBoxes.es;
    #import lib/tokenExists/1.0.0/tokenExists.es;
    #import lib/stakeRecordLockedUntil/1.0.0/stakeRecordLockedUntil.es;
    #import lib/stakeRecordStake/1.0.0/stakeRecordStake.es;
    #import lib/stakeRecordProfits/1.0.0/stakeRecordProfits.es;

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
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateTree: AvlTree     = stakeState.R4[Coll[AvlTree]].get(0)
    val participationTree: AvlTree  = stakeState.R4[Coll[AvlTree]].get(1)
    val stakeStateR5: Coll[Long]    = stakeState.R5[Coll[Long]].get
    val nextEmission: Long          = stakeStateR5(0)
    val totalStaked: Long           = stakeStateR5(1)
    val stakers: Long               = stakeStateR5(2)
    val r5Rest: Coll[Long]          = stakeStateR5.slice(3, stakeStateR5.size)

    val stakeStateR6: Coll[Coll[Long]] = stakeState.R6[Coll[Coll[Long]]].get

    val stakeStateR7: Coll[(AvlTree, AvlTree)] = 
        stakeState.R7[Coll[(AvlTree, AvlTree)]].get

    val stakeStateR8: Coll[Long] = stakeState.R8[Coll[Long]].get

    val stakeStateOTree: AvlTree     = stakeStateO.R4[Coll[AvlTree]].get(0)
    val participationTreeO: AvlTree  = stakeStateO.R4[Coll[AvlTree]].get(1)
    val stakeStateOR5: Coll[Long]    = stakeStateO.R5[Coll[Long]].get
    val nextEmissionO: Long          = stakeStateOR5(0)
    val totalStakedO: Long           = stakeStateOR5(1)
    val stakersO: Long               = stakeStateOR5(2)
    val r5RestO: Coll[Long]          = stakeStateOR5.slice(3, stakeStateOR5.size)

    val stakeStateOR6: Coll[Coll[Long]] = stakeStateO.R6[Coll[Coll[Long]]].get

    val stakeStateOR7: Coll[(AvlTree, AvlTree)] = 
        stakeStateO.R7[Coll[(AvlTree, AvlTree)]].get

    val stakeStateOR8: Coll[Long] = stakeStateO.R8[Coll[Long]].get

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

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
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

    val stakeRecord: Coll[Byte] = stakeStateTree.get(keys(0), proof).get

    val lockedUntil: Long = stakeRecordLockedUntil(stakeRecord)

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
        participationTreeO == participationTree,
        nextEmissionO == nextEmission,
        r5RestO == r5Rest,
        stakeStateOR6 == stakeStateR6,
        stakeStateOR7 == stakeStateR7,
        stakeStateOR8 == stakeStateR8
    ))

    val keyInInput: Boolean = tokenExists((INPUTS, keys(0)))

    val tokensUnstaked: Boolean = allOf(Coll(
        currentStakeAmount == 
            (stakeState.tokens(1)._2 - stakeStateO.tokens(1)._2),
        currentStakeAmount == totalStaked - totalStakedO
    ))

    val correctErgProfit: Boolean = 
        currentProfits(0) == stakeState.value - stakeStateO.value

    val singleStakeOp: Boolean = keys.size == 1

    val correctStakersCount: Boolean = stakers - 1L == stakersO

    val correctNewState: Boolean = 
        stakeStateTree.remove(keys, removeProof).get.digest == 
            stakeStateOTree.digest

    val unlocked: Boolean = CONTEXT.preHeader.timestamp > lockedUntil
        
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