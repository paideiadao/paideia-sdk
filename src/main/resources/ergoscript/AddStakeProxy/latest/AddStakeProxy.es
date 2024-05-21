/** 
 * 
 *
 * @return
 */
@contract def addStakeProxy(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/validRefund/1.0.0/validRefund.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;
    
    // Refund logic
    sigmaProp(
    if (INPUTS(0).id == SELF.id) {
        validRefund((SELF, (OUTPUTS(0), (SELF.R4[Coll[Byte]].get, 15))))
    } else {
    /**
     *
     *  AddStakeProxy
     *
     *  This contract ensures the tokens are added to the correct stake and
     *  the stake key is returned to the user
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaStakingStateTokenId: Coll[Byte] = _IM_PAIDEIA_STAKING_STATE_TOKENID

    val stakeInfoOffset: Int = 8

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box    = INPUTS(0)
    val addStakeProxy: Box = SELF

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

    val userPropositionBytes: Coll[Byte] = addStakeProxy.R4[Coll[Byte]].get
    val addStakeAmount: Long             = addStakeProxy.R5[Long].get

    val stakeStateTree: AvlTree = stakeState.R4[Coll[AvlTree]].get(0)

    val stakeStateTreeO: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(0)

    val configTree: AvlTree = config.R4[AvlTree].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    val stakeOperations: Coll[(Coll[Byte], Coll[Byte])]  = 
        getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get

    val proof: Coll[Byte] = getVar[Coll[Byte]](2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            imPaideiaStakingStateTokenId
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte] = bytearrayToTokenId(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val newStakeAmount: Long = 
        byteArrayToLong(
            stakeOperations(0)._2.slice(stakeInfoOffset,stakeInfoOffset+8)
        )

    val currentStakeState: Coll[Byte] = 
        stakeStateTree.get(stakeOperations(0)._1, proof).get

    val currentStakeAmount: Long = 
        byteArrayToLong(
            currentStakeState.slice(stakeInfoOffset,stakeInfoOffset+8)
        )

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfigTokenId: Boolean = config.tokens(0)._1 == imPaideiaDaoKey
    
    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId
    
    val keyInOutput: Boolean = userO.tokens(0)._1 == stakeOperations(0)._1

    val tokensStaked: Boolean = 
        newStakeAmount - currentStakeAmount == addStakeAmount

    val singleStakeOp: Boolean = stakeOperations.size == 1

    val correctNewState: Boolean = 
        stakeStateTree.update(stakeOperations, proof).get.digest == 
        stakeStateTreeO.digest

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    allOf(Coll(
        correctConfigTokenId,
        correctStakeState,
        userO.propositionBytes == userPropositionBytes,
        keyInOutput,
        tokensStaked,
        singleStakeOp,
        correctNewState
    ))
    }
    )
}