/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def stakeProfitShare(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;

    /**
     *
     *  StakeProfitShare
     *
     *  Companion contract to the main stake contract ensuring that any shared
     *  profit gets registered correctly.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val imPaideiaContractsStakingProfitShare: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_PROFITSHARE

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box  = INPUTS(0)
    val profitShare: Box = SELF

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
    val profitShareO: Box = OUTPUTS(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateR4: Coll[AvlTree]  = stakeState.R4[Coll[AvlTree]].get
    val stakeStateR5: Coll[Long] = stakeState.R5[Coll[Long]].get
    val stakeStateR6: Coll[Coll[Long]]  = stakeState.R6[Coll[Coll[Long]]].get
    val stakeStateR7: Coll[(AvlTree, AvlTree)]  = stakeState.R7[Coll[(AvlTree, AvlTree)]].get
    val stakeStateR8: Coll[Long]  = stakeState.R8[Coll[Long]].get

    val stakeStateOR4: Coll[AvlTree]  = stakeStateO.R4[Coll[AvlTree]].get
    val stakeStateOR5: Coll[Long] = stakeStateO.R5[Coll[Long]].get
    val stakeStateOR6: Coll[Coll[Long]]  = stakeStateO.R6[Coll[Coll[Long]]].get
    val stakeStateOR7: Coll[(AvlTree, AvlTree)]  = stakeStateO.R7[Coll[(AvlTree, AvlTree)]].get
    val stakeStateOR8: Coll[Long]  = stakeStateO.R8[Coll[Long]].get

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
            imPaideiaContractsStakingProfitShare,
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte]       = bytearrayToTokenId(configValues(0))
    val profitShareContractHash: Coll[Byte] = bytearrayToContractHash(configValues(1))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val profit: Coll[Long] = stakeStateR5.slice(5,stakeStateR5.size)

    val r5Rest: Coll[Long] = stakeStateR5.slice(0,5)

    val outputProfit: Coll[Long] = stakeStateOR5.slice(5,stakeStateOR5.size)

    val r5RestO: Coll[Long] = stakeStateOR5.slice(0,5)

    val ergProfit: Long = stakeStateO.value - stakeState.value

    val govProfit: Long = stakeStateO.tokens(1)._2 - stakeState.tokens(1)._2

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == imPaideiaDaoKey

    val correctStakeState: Boolean = allOf(Coll(
        stakeState.tokens(0)._1 == stakeStateTokenId,
        stakeStateOR4 == stakeStateR4,
        r5RestO == r5Rest,
        stakeStateOR6 == stakeStateR6,
        stakeStateOR7 == stakeStateR7,
        stakeStateOR8 == stakeStateR8
    ))

    val correctErgProfit: Boolean = 
        ergProfit >= 0L && outputProfit(1) - profit(1) == ergProfit
    
    val correctGovProfit: Boolean = 
        govProfit >= 0L && outputProfit(0) - profit(0) == govProfit

    val selfOutput: Boolean = allOf(Coll(
        blake2b256(profitShareO.propositionBytes) == profitShareContractHash,
        profitShareO.value >= profitShare.value
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctConfig,
        correctStakeState,
        correctErgProfit,
        correctGovProfit,
        stakeStateO.tokens.size >= stakeState.tokens.size,
        selfOutput
    )))
}