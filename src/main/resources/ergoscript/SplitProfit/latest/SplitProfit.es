{
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;
    #import lib/tokensInBoxesAll/1.0.0/tokensInBoxesAll.es;
    #import lib/tokensInBoxes/1.0.0/tokensInBoxes.es;
    /**
     *
     *  SplitProfit
     *
     *  This contract will make sure any assets deposited in its address are
     *  split correctly between the stakers and the treasury
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY 
    val imPaideiaContractsTreasury: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_TREASURY
    val imPaideiaProfitSharingPct: Coll[Byte]  = _IM_PAIDEIA_PROFIT_SHARING_PCT

    val imPaideiaStakingProfitTokenIds: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS

    val imPaideiaDaoGovernanceTokenId: Coll[Byte] = 
        _IM_PAIDEIA_DAO_GOVERNANCE_TOKENID

    val imPaideiaContractsStakingState: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_STATE

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    //Only relevant for profitsharepct > 0
    val stakingState: Box = INPUTS(0)

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

    //Only relevant for profitsharepct > 0
    val stakingStateO: Box = OUTPUTS(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

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
            imPaideiaContractsTreasury,
            imPaideiaContractsStakingState,
            imPaideiaProfitSharingPct,
            imPaideiaDaoGovernanceTokenId,
            imPaideiaStakingProfitTokenIds
        ),
        configProof
    )

    val treasuryContractHash: Coll[Byte]     = bytearrayToContractHash(configValues(0))
    val stakingStateContractHash: Coll[Byte] = bytearrayToContractHash(configValues(1))
    val profitSharingPct: Byte               = configValues(2).get(1)
    val governanceTokenId: Coll[Byte]        = bytearrayToTokenId(configValues(3))

    val profitTokenIds: Coll[Coll[Byte]] = 
        configValues(4).get.slice(0,(configValues(4).get.size-6)/37).indices
        .map{
            (i: Int) =>
            configValues(4).get.slice(6+(37*i)+5,6+(37*(i+1)))
        }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val treasuryO: Box = OUTPUTS.filter{
        (b: Box) => 
        blake2b256(b.propositionBytes) == treasuryContractHash
    }(0)

    val minerO: Box = OUTPUTS.filter{
        (b: Box) => 
        blake2b256(b.propositionBytes) != treasuryContractHash && 
        blake2b256(b.propositionBytes) != stakingStateContractHash
    }(0)

    val tokensIn: Long  = tokensInBoxesAll(INPUTS)
    val tokensOut: Long = tokensInBoxesAll(OUTPUTS)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfigTokenId: Boolean = config.tokens(0)._1 == daoKey

    val generalConditions: Boolean = allOf(
        Coll(
            minerO.value <= 5000000L,
            minerO.tokens.size == 0,
            tokensIn == tokensOut,
            treasuryO.value >= 1000000L
        )
    )

    val validTx: Boolean = if (profitSharingPct <= 0) {
        OUTPUTS.size == 2
    } else {
        val tokenSplits: Boolean = Coll(governanceTokenId).append(profitTokenIds)
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

    sigmaProp(correctConfigTokenId && validTx)
}