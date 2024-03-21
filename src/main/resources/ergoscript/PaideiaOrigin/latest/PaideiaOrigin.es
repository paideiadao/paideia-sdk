{
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    
    /**
     *
     *  CastVote
     *
     *  This contract ensures the is added correctly to the proposal tally and
     *  the stake key is returned to the user.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaDaoKey: Coll[Byte]              = _PAIDEIA_DAO_KEY
    val paideiaTokenId: Coll[Byte]             = _PAIDEIA_TOKEN_ID
    val imPaideiaFeesCreateDaoErg: Coll[Byte]  = _IM_PAIDEIA_FEES_CREATEDAO_ERG
    val imPaideiaFeesCreateDaoPai: Coll[Byte]  = _IM_PAIDEIA_FEES_CREATEDAO_PAIDEIA
    val imPaideiaContractsProtoDao: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_PROTODAO

    val imPaideiaContractsProtoDaoProxy: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_PROTODAOPROXY

    val imPaideiaContractsSplitProfit: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val protoDAOProxy: Box = INPUTS(0)
    val origin: Box        = SELF

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfig: Box = CONTEXT.dataInputs(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val protoDaoO: Box    = OUTPUTS(0)
    val originO: Box      = OUTPUTS(1)
    val profitShareO: Box = OUTPUTS(2)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfigTree: AvlTree = paideiaConfig.R4[AvlTree].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = paideiaConfigTree.getMany(
        Coll(
            imPaideiaFeesCreateDaoErg,
            imPaideiaFeesCreateDaoPai,
            imPaideiaContractsProtoDao,
            imPaideiaContractsProtoDaoProxy,
            imPaideiaContractsSplitProfit
        ),
        paideiaConfigProof
    )

    val createDAOFeeErg: Long = byteArrayToLong(configValues(0).get.slice(1,9))

    val createDAOFeePaideia: Long = 
        byteArrayToLong(configValues(1).get.slice(1,9))

    val protoDAOContractHash: Coll[Byte]      = bytearrayToContractHash(configValues(2))
    val protoDAOProxyContractHash: Coll[Byte] = bytearrayToContractHash(configValues(3))
    val profitShareContractHash: Coll[Byte]   = bytearrayToContractHash(configValues(4))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig = paideiaConfig.tokens(0)._1 == paideiaDaoKey

    val correctPaideiaOrigin: Boolean = allOf(
        Coll(
            originO.tokens(0)        == origin.tokens(0),
            originO.tokens(1)._1     == origin.tokens(1)._1,
            originO.tokens(1)._2     == origin.tokens(1)._2 - 1L,
            originO.tokens.size      == 2,
            originO.propositionBytes == origin.propositionBytes,
            originO.value            >= origin.value
        )
    )

    val correctProtoOut: Boolean = allOf(
        Coll(
            blake2b256(protoDaoO.propositionBytes) == protoDAOContractHash,
            protoDaoO.tokens(0)._1 == origin.tokens(1)._1,
            protoDaoO.tokens(0)._2 == 1L
        )
    )

    val correctPaideiaProfit: Boolean = allOf(
        Coll(
            blake2b256(profitShareO.propositionBytes) == profitShareContractHash,
            profitShareO.value == createDAOFeeErg + 1000000L,
            profitShareO.tokens(0)._1 == paideiaTokenId,
            profitShareO.tokens(0)._2 == createDAOFeePaideia,
        )
    )

    val correctProtoDaoProxy: Boolean = 
        blake2b256(protoDAOProxy.propositionBytes) == protoDAOProxyContractHash

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(
        Coll(
            correctConfig,
            correctPaideiaOrigin,
            correctProtoOut,
            correctPaideiaProfit,
            correctProtoDaoProxy
        )
    ))
}