{

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

    val daoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val imPaideiaContractsStakingUnstake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE

    val imPaideiaStakingProfitTokenIds: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS

    val stakeInfoOffset: Int = 8

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = INPUTS(0)
    val unstake: Box    = SELF
    val proxy: Box      = INPUTS(2)

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

    val stakeStateTree: AvlTree  = stakeState.R4[Coll[AvlTree]].get(0)
    val stakeStateR5: Coll[Long] = stakeState.R5[Coll[Long]].get
    val totalStaked: Long        = stakeStateR5(1)

    val stakeStateOTree: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(0)
    val totalStakedO: Long       = stakeStateO.R5[Coll[Long]].get(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    val operations: Coll[(Coll[Byte],Coll[Byte])] = 
        getVar[Coll[(Coll[Byte],Coll[Byte])]](1).get

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
            imPaideiaContractsStakingUnstake,
            imPaideiaStakingProfitTokenIds
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte]   = configValues(0).get.slice(6,38)
    val unstakeContractHash: Coll[Byte] = configValues(1).get.slice(1,33)
    val profitTokenIds: Coll[Byte]      = configValues(2).get

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

    val longIndices: Coll[Int] = profit.indices.map{(i: Int) => i*8}

    val keys: Coll[Coll[Byte]] = operations.map{
        (kv: (Coll[Byte], Coll[Byte])) => kv._1
    }

    val currentStakeState: Coll[Byte] = stakeStateTree.get(keys(0), proof).get

    val currentProfits: Coll[Long] = longIndices.map{
        (i: Int) => 
        byteArrayToLong(
            currentStakeState.slice(i+stakeInfoOffset,i+8+stakeInfoOffset)
        )
    }

    val currentStakeAmount: Long = currentProfits(0)
        
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId

    val keyInInput: Boolean = proxy.tokens(0)._1 == keys(0)

    val tokensUnstaked: Boolean = allOf(Coll(
        currentStakeAmount == 
            (stakeState.tokens(1)._2 - stakeStateO.tokens(1)._2),
        currentStakeAmount == totalStaked - totalStakedO
    ))

    val correctErgProfit: Boolean = 
        currentProfits(1) == stakeState.value - stakeStateO.value

    val correctTokenProfit: Boolean = stakeState.tokens
        .slice(2,stakeState.tokens.size).forall{
                (token: (Coll[Byte], Long)) =>
                val profitIndex: Int = whiteListedTokenIds.indexOf(token._1,-3)
                val tokenAmountInOutput: Long = stakeStateO.tokens.fold(0L, {
                    (z: Long, outputToken: (Coll[Byte], Long)) => 
                    if (outputToken._1 == token._1) z + outputToken._2 else z
                })
                token._2 - tokenAmountInOutput == currentProfits(profitIndex+2)
            }

    val singleStakeOp: Boolean = keys.size == 1

    val correctNewState: Boolean = 
        stakeStateTree.remove(keys, removeProof).get.digest == 
            stakeStateOTree.digest
        
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
        correctTokenProfit,
        singleStakeOp,
        correctNewState,
        selfOutput
    )))
}