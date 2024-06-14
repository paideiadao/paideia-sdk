/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def stakeProfitShare(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/stakeState/1.0.0/stakeState.es;

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

    val ergProfit: Long = stakeStateO.value - stakeState.value

    val govProfit: Long = stakeStateO.tokens(1)._2 - stakeState.tokens(1)._2

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == imPaideiaDaoKey

    val correctStakeState: Boolean = allOf(Coll(
        stakeState.tokens(0)._1         == stakeStateTokenId,
        stakeTree(stakeStateO)          == stakeTree(stakeState),
        participationTree(stakeStateO)  == participationTree(stakeState),
        nextEmission(stakeStateO)       == nextEmission(stakeState),
        totalStaked(stakeStateO)        == totalStaked(stakeState),
        stakers(stakeStateO)            == stakers(stakeState),
        votedThisCycle(stakeStateO)     == votedThisCycle(stakeState),
        votesCastThisCycle(stakeStateO) == votesCastThisCycle(stakeState),
        snapshotValues(stakeStateO)     == snapshotValues(stakeState),
        snapshotTrees(stakeStateO)      == snapshotTrees(stakeState),
        snapshotProfit(stakeStateO)     == snapshotProfit(stakeState)
    ))

    val correctErgProfit: Boolean = 
        ergProfit >= 0L && profit(stakeStateO)(1) - profit(stakeState)(1) == ergProfit
    
    val correctGovProfit: Boolean = 
        govProfit >= 0L && profit(stakeStateO)(0) - profit(stakeState)(0) == govProfit

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