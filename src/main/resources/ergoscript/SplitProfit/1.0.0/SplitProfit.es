/** 
 *  This contract will make sure any assets deposited in its address are
 *  split correctly between the stakers and the treasury
 *
 * @param imPaideiaDaoKey Token ID of the dao key 
 * @param stakeStateTokenId Token ID of the stake state nft
 *
 * @return
 */
@contract def splitProfit(imPaideiaDaoKey: Coll[Byte], stakeStateTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/box/1.0.0/box.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsTreasury: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_TREASURY
    val imPaideiaProfitSharingPct: Coll[Byte]  = _IM_PAIDEIA_PROFIT_SHARING_PCT

    val imPaideiaDaoGovernanceTokenId: Coll[Byte] = 
        _IM_PAIDEIA_DAO_GOVERNANCE_TOKENID

    val imPaideiaContractsStakingState: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_STATE

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
            imPaideiaContractsTreasury,
            imPaideiaContractsStakingState,
            imPaideiaProfitSharingPct,
            imPaideiaDaoGovernanceTokenId
        ),
        configProof
    )

    val treasuryContractHash: Coll[Byte]     = bytearrayToContractHash(configValues(0))
    val stakingStateContractHash: Coll[Byte] = bytearrayToContractHash(configValues(1))
    val profitSharingPct: Byte               = configValues(2).get(1)
    val governanceTokenId: Coll[Byte]        = bytearrayToTokenId(configValues(3))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val treasuryO: Box = filterByHash((OUTPUTS,treasuryContractHash))(0)

    val minerO: Box = OUTPUTS.filter{
        (b: Box) => 
        blake2b256(b.propositionBytes) != treasuryContractHash && 
        blake2b256(b.propositionBytes) != stakingStateContractHash
    }(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val generalConditions: Boolean = allOf(
        Coll(
            minerO.value <= 5000000L,
            minerO.tokens.size == 0,
            tokensInBoxesAll(INPUTS) == tokensInBoxesAll(OUTPUTS),
            treasuryO.value >= 1000000L
        )
    )

    val validTx: Boolean = if (profitSharingPct <= 0) {
        OUTPUTS.size == 2
    } else {
        val stakingState: Box = filterByTokenId((INPUTS,stakeStateTokenId))(0)
        val stakingStateO: Box = filterByTokenId((OUTPUTS, stakeStateTokenId))(0)
        val tokenSplits: Boolean = Coll(governanceTokenId)
        .forall{
            (tokenId: Coll[Byte]) => {
                val stakingInputTokens: Long = tokensInBoxes((Coll(stakingState), tokenId))
                val stakingOutputTokens: Long = tokensInBoxes((Coll(stakingStateO), tokenId))
                val treasuryTokens: Long = tokensInBoxes((Coll(treasuryO), tokenId))

                (stakingOutputTokens - stakingInputTokens + 
                treasuryTokens)*profitSharingPct/100 == 
                (stakingOutputTokens - stakingInputTokens)
        }}

        val ergSplit: Boolean = 
            (stakingStateO.value - stakingState.value + 
            (treasuryO.value-1000000L))*profitSharingPct/100 == 
            (stakingStateO.value - stakingState.value)

        allOf(Coll(
            OUTPUTS.size == 4,
            blake2b256(stakingStateO.propositionBytes) == 
                stakingStateContractHash,
            ergSplit,
            tokenSplits
        ))

    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(generalConditions && validTx)
}