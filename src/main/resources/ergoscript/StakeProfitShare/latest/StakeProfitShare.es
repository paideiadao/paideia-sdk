{

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

    val daoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val imPaideiaContractsStakingProfitShare: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_PROFITSHARE

    val imPaideiaStakingProfitTokenIds: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS

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

    val stakeStateTree: AvlTree  = stakeState.R4[Coll[AvlTree]].get(0)
    val stakeStateR5: Coll[Long] = stakeState.R5[Coll[Long]].get

    val stakeStateOR5: Coll[Long] = stakeStateO.R5[Coll[Long]].get

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
            imPaideiaStakingProfitTokenIds
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte]       = configValues(0).get.slice(6,38)
    val profitShareContractHash: Coll[Byte] = configValues(1).get.slice(1,33)
    val profitTokenIds: Coll[Byte]          = configValues(2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val whiteListedTokenIds: Coll[Coll[Byte]] = 
        profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{
            (i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }

    val profit: Coll[Long] = stakeStateR5.slice(5,stakeStateR5.size).append(
        whiteListedTokenIds.slice(
            stakeStateR5.size-4,
            whiteListedTokenIds.size
        ).map{(tokId: Coll[Byte]) => 0L})

    val outputProfit: Coll[Long] = stakeStateOR5.slice(5,stakeStateOR5.size)

    val ergProfit: Long = stakeStateO.value - stakeState.value

    val govProfit: Long = stakeStateO.tokens(1)._2 - stakeState.tokens(1)._2

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId

    val correctErgProfit: Boolean = 
        ergProfit >= 0L && outputProfit(1) - profit(1) == ergProfit
    
    val correctGovProfit: Boolean = 
        govProfit >= 0L && outputProfit(0) - profit(0) == govProfit

    val correctUpdatedProfit: Boolean = 
        stakeState.tokens.slice(2,stakeState.tokens.size)
        .zip(stakeStateO.tokens.slice(2,stakeState.tokens.size))
        .forall{
            (io: ((Coll[Byte],Long),(Coll[Byte],Long))) =>
            val i = io._1
            val o = io._2
            val profitIndex = whiteListedTokenIds.indexOf(i._1,-1)
            val tokenProfit = o._2 - i._2
            allOf(Coll(
                i._1 == o._1,
                profitIndex >= 0,
                tokenProfit == outputProfit(profitIndex+2)-profit(profitIndex+2),
                tokenProfit >= 0L
            ))
        }
        
    val correctNewProfit: Boolean = stakeStateO.tokens
        .slice(stakeState.tokens.size,stakeStateO.tokens.size)
        .forall{
            (o: (Coll[Byte],Long)) =>
            val profitIndex = whiteListedTokenIds.indexOf(o._1,-3)
            val tokenProfit = o._2
            allOf(Coll(
                profitIndex >= 0,
                tokenProfit == outputProfit(profitIndex+2),
                tokenProfit >= 0L
            ))
        }

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
        correctUpdatedProfit,
        correctNewProfit,
        stakeStateO.tokens.size >= stakeState.tokens.size,
        selfOutput
    )))
}