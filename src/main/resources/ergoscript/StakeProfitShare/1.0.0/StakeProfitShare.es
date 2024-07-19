/** 
 * This contract is a companion contract to the main stake contract. It ensures 
 * profit is properly updated according to the shared profit
 *
 * @param imPaideiaDaoKey Token ID of the DAO key
 * @param stakeStateTokenId Token ID of the stake state NFT
 *
 * @return
 */
@contract def stakeProfitShare(imPaideiaDaoKey: Coll[Byte], stakeStateTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/box/1.0.0/box.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsStakingProfitShare: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_PROFITSHARE

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box  = filterByTokenId((INPUTS, stakeStateTokenId))(0)
    val profitShare: Box = SELF

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
            imPaideiaContractsStakingProfitShare,
        ),
        configProof
    )

    val profitShareContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeStateO: Box  = filterByTokenId((OUTPUTS,stakeStateTokenId))(0)
    val profitShareO: Box = filterByHash((OUTPUTS, profitShareContractHash))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val ergProfit: Long = stakeStateO.value - stakeState.value

    val govProfit: Long = govToken(stakeStateO)._2 - govToken(stakeState)._2

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctStakeState: Boolean = allOf(Coll(
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

    //Make sure the change in profit matches the assets and that it is not negative

    val correctErgProfit: Boolean = 
        ergProfit >= 0L && profit(stakeStateO)(1) - profit(stakeState)(1) == ergProfit
    
    val correctGovProfit: Boolean = 
        govProfit >= 0L && profit(stakeStateO)(0) - profit(stakeState)(0) == govProfit

    val selfOutput: Boolean = profitShareO.value >= profitShare.value

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctStakeState,
        correctErgProfit,
        correctGovProfit,
        stakeStateO.tokens.size >= stakeState.tokens.size,
        selfOutput
    )))
}