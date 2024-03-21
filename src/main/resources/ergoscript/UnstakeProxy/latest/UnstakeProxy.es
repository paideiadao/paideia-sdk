{
    #import lib/validRefund/1.0.0/validRefund.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;
    #import lib/tokensInBoxes/1.0.0/tokensInBoxes.es;

    // Refund logic
    sigmaProp(
    if (INPUTS(0).id == SELF.id) {
        validRefund((SELF, (OUTPUTS(0), (SELF.R4[Coll[Byte]].get, 15))))
    } else {
    /**
     *
     *  UnstakeProxy
     *
     *  Proxy to help the user unstake assets from the staking setup.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKENID

    val imPaideiaStakingProfitTokenIds: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS

    val stakeInfoOffset: Int = 8

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = INPUTS(0)
    val proxy: Box      = SELF

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
    val userO: Box       = OUTPUTS(2)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateTree: AvlTree = stakeState.R4[Coll[AvlTree]].get(0)

    val userProp: Coll[Byte]       = proxy.R4[Coll[Byte]].get
    val newStakeRecord: Coll[Byte] = proxy.R5[Coll[Byte]].get

    val stakeStateOTree: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get
    val proof: Coll[Byte]       = getVar[Coll[Byte]](1).get
    val removeProof: Coll[Byte] = getVar[Coll[Byte]](2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            imPaideiaStakeStateTokenId,
            imPaideiaStakingProfitTokenIds
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte] = bytearrayToTokenId(configValues(0))
    val profitTokenIds: Coll[Byte]    = configValues(1).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val newStake: Long = byteArrayToLong(
        newStakeRecord.slice(stakeInfoOffset,stakeInfoOffset+8)
    )

    val longIndices: Coll[Int] = 
        newStakeRecord.slice(0,newStakeRecord.size/8-(stakeInfoOffset/8)).indices

    val whiteListedTokenIds: Coll[Coll[Byte]] = 
        profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{
            (i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }

    val stakeKey: Coll[Byte] = proxy.tokens(0)._1

    val stakeOperations: Coll[(Coll[Byte],Coll[Byte])] = 
        Coll((stakeKey,newStakeRecord))

    val currentStakeState: Coll[Byte] = 
        stakeStateTree.get(stakeOperations(0)._1, proof).get

    val currentProfits: Coll[Long] = longIndices.map{
        (i: Int) => 
        byteArrayToLong(
            currentStakeState.slice(i*8+stakeInfoOffset,i*8+8+stakeInfoOffset)
        )
    }

    val newProfits: Coll[Long] = longIndices.map{
        (i: Int) => 
        byteArrayToLong(
            stakeOperations(0)._2.slice(
                i*8+stakeInfoOffset,
                i*8+8+stakeInfoOffset
            )
        )
    }
    
    val combinedProfit: Coll[(Long, Long)] = currentProfits.zip(newProfits)

    val currentStakeAmount: Long = currentProfits(0)

    val newStakeAmount: Long = newProfits(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId

    val tokensUnstaked: Boolean = 
        currentStakeAmount - newStakeAmount == tokensInBoxes((Coll(userO), stakeState.tokens(1)._1))

    val correctErgProfit: Boolean = 
        currentProfits(1) - newProfits(1) == userO.value-1000000L

    val correctTokenProfit: Boolean = 
        stakeState.tokens.slice(2,stakeState.tokens.size).forall{
            (token: (Coll[Byte], Long)) =>
            val profitIndex: Int = whiteListedTokenIds.indexOf(token._1,-3)
            val tokenAmountInOutput: Long = tokensInBoxes((Coll(userO), token._1))
            tokenAmountInOutput == 
                currentProfits(profitIndex+2) - newProfits(profitIndex+2)
        }

    val keyPresent: Boolean = 
        if (newStake > 0)
            userO.tokens(0)._1 == stakeKey
        else
            proxy.tokens(0)._1 == stakeKey

    val correctUserOutput: Boolean = userO.propositionBytes == userProp

    val correctNewState: Boolean =
        if (newStake > 0)
            stakeStateTree.update(stakeOperations, proof).get.digest == 
            stakeStateOTree.digest
        else
            stakeStateTree.remove(Coll(stakeKey), removeProof).get.digest == 
            stakeStateOTree.digest
            
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    allOf(Coll(
        correctConfig,
        correctStakeState,
        tokensUnstaked,
        correctErgProfit,
        correctTokenProfit,
        keyPresent,
        correctNewState,
        correctUserOutput
    ))})
}