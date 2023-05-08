{

    /**
     *
     *  Stake
     *
     *  Companion contract to main stake state contract handling the create
     *  new stake logic
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

    val imPaideiaStakeProfitTokenIds: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val imPaideiaContractsStakeState: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_STAKE

    val stakeInfoOffset: Int = 8

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = INPUTS(0)
    val stake: Box      = SELF

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
    val stakeO: Box      = OUTPUTS(1)
    val userO: Box       = OUTPUTS(2)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateTree: AvlTree  = stakeState.R4[Coll[AvlTree]].get(0)
    val stakeStateR5: Coll[Long] = stakeState.R5[Coll[Long]].get
    val totalStaked: Long        = stakeStateR5(1)

    val stakeStateTreeO: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(0)
    val totalStakedO: Long       = stakeStateO.R5[Coll[Long]].get(1)

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
            imPaideiaContractsStakeState,
            imPaideiaStakeStateTokenId,
            imPaideiaStakeProfitTokenIds
        ),
        configProof
    )

    val stakeContractHash: Coll[Byte] = configValues(0).get.slice(1,33)
    val stakeStateTokenId: Coll[Byte] = configValues(1).get.slice(6,38)
    val profitTokenIds: Coll[Byte] = configValues(2).get

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

    val profit: Coll[Long] = stakeStateR5.slice(5,stakeStateR5.size)
        .append(
            whiteListedTokenIds.slice(
                stakeStateR5.size-4,
                whiteListedTokenIds.size).map{
                    (tokId: Coll[Byte]) => 0L
                }
            )

    val longIndices: Coll[Int] = profit.indices.map{(i: Int) => i*8}

    val mintedKey: Coll[Byte] = userO.tokens(0)

    val stakeRecord: Coll[Long] = longIndices.map{
        (i: Int) => 
        byteArrayToLong(
            stakeOperations(0)._2.slice(i+stakeInfoOffset,i+8+stakeInfoOffset)
        )
    }

    val stakeAmount: Long = stakeRecord(0)

    val updatedTree: AvlTree = stakeStateTree.insert(stakeOperations, proof).get
        
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfigTokenId: Boolean = config.tokens(0)._1 == daoKey

    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId

    val zeroReward: Boolean = stakeRecord.slice(1,stakeRecord.size).forall{
        (l: Long) => l==0L
    }

    val correctKeyMinted: Boolean = stakeState.id == mintedKey._1 && 
        stakeState.id == stakeOperations(0)._1
    
    val correctAmountMinted: Boolean = OUTPUTS.flatMap{(b: Box) => b.tokens}
        .fold(0L, {
            (z: Long, t: (Coll[Byte], Long)) => 
            z + (if (t._1 == mintedKey._1) 
                    t._2 
                else 
                    0L)
            }) == 1L

    val tokensStaked: Boolean = 
        stakeAmount == 
        (stakeStateO.tokens(1)._2 - stakeState.tokens(1)._2) && 
        stakeAmount == totalStakedO - totalStaked

    val singleStakeOp: Boolean = stakeOperations.size == 1

    val correctNewState: Boolean = updatedTree.digest == stakeStateTreeO.digest

    val selfOutput = allOf(Coll(
        blake2b256(stakeO.propositionBytes) == stakeContractHash,
        stakeO.value >= stake.value
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        correctStakeState,
        correctKeyMinted,
        correctAmountMinted,
        tokensStaked,
        singleStakeOp,
        correctNewState,
        zeroReward,
        selfOutput
    )))
}