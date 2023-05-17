{
    // Refund logic
    sigmaProp(
    if (INPUTS(0).id == SELF.id) {
        allOf(Coll(
            OUTPUTS(0).value >= SELF.value - 1000000L,
            OUTPUTS(0).tokens == SELF.tokens,
            OUTPUTS(0).propositionBytes == SELF.R4[Coll[Byte]].get,
            CONTEXT.preHeader.height >= SELF.creationInfo._1 + 30
        ))
    } else {
    /**
     *
     *  StakeProxy
     *
     *  Helps the user create a new stake and ensures the stake key gets
     *  returned to the user.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoKey: Coll[Byte]               = _IM_PAIDEIA_DAO_KEY
    val imPaideiaDaoName: Coll[Byte]     = _IM_PAIDEIA_DAO_NAME
    val stakeKeyText: Coll[Byte]         = _STAKE_KEY
    val poweredByPaideiaText: Coll[Byte] = _POWERED_BY_PAIDEIA

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKENID

    val stakeInfoOffset: Int  = 8
    val decimals0: Coll[Byte] = Coll(48.toByte)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = INPUTS(0)
    val stakeProxy: Box = SELF

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

    val userProp: Coll[Byte] = stakeProxy.R4[Coll[Byte]].get
    val stakeAmount: Long    = stakeProxy.R5[Long].get

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateTree: AvlTree  = stakeState.R4[Coll[AvlTree]].get(0)
    val stakeStateR5: Coll[Long] = stakeState.R5[Coll[Long]].get
    val totalStaked: Long        = stakeStateR5(1)

    val stakeStateOTree: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(0)
    val totalStakedO: Long       = stakeStateO.R5[Coll[Long]].get(1)

    val mintName: Coll[Byte]     = userO.R4[Coll[Byte]].get
    val mintDesc: Coll[Byte]     = userO.R5[Coll[Byte]].get
    val mintDecimals: Coll[Byte] = userO.R6[Coll[Byte]].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get
    val stakeOperations: Coll[(Coll[Byte], Coll[Byte])] = 
        getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get

    val proof: Coll[Byte] = getVar[Coll[Byte]](2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            imPaideiaStakeStateTokenId,
            imPaideiaDaoName
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte] = configValues(0).get.slice(6,38)

    val daoName: Coll[Byte] = 
        configValues(1).get.slice(5,configValues(1).get.size)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val profit: Coll[Long] = stakeStateR5.slice(5,stakeStateR5.size)

    val longIndices: Coll[Int] = profit.indices.map{(i: Int) => i*8}

    val mintedKey: (Coll[Byte], Long) = userO.tokens(0)

    val stakeRecord: Coll[Long] = longIndices.map{
        (i: Int) => 
        byteArrayToLong(
            stakeOperations(0)._2.slice(i+stakeInfoOffset,i+8+stakeInfoOffset)
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId
    
    val zeroReward: Boolean = stakeRecord.slice(1,stakeRecord.size)
        .forall{(l: Long) => l==0L}

    val correctKeyMinted: Boolean = allOf(Coll(
        stakeState.id == mintedKey._1,
        stakeState.id == stakeOperations(0)._1,
        mintName == daoName++stakeKeyText,
        mintDesc == poweredByPaideiaText,
        mintDecimals == decimals0
    ))

    val correctAmountMinted: Boolean = OUTPUTS.flatMap{(b: Box) => b.tokens}
        .fold(0L, {
            (z: Long, token: (Coll[Byte], Long)) => 
            if (token._1==stakeState.id) z + token._2 else z
        }) == 1L

    val tokensStaked: Boolean = allOf(Coll(
        stakeAmount == (stakeStateO.tokens(1)._2 - stakeState.tokens(1)._2),
        stakeAmount == totalStakedO - totalStaked
    ))

    val singleStakeOp: Boolean = stakeOperations.size == 1

    val correctNewState: Boolean = stakeStateTree.insert(
        stakeOperations, 
        proof).get.digest == stakeStateOTree.digest

    val userGetsKey: Boolean = userO.propositionBytes == userProp

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    allOf(Coll(
        correctConfig,
        correctStakeState,
        correctKeyMinted,
        correctAmountMinted,
        tokensStaked,
        singleStakeOp,
        correctNewState,
        zeroReward,
        userGetsKey
    ))})
}