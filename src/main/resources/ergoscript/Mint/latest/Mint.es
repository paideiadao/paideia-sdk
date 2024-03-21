{
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;

    /**
     *
     *  Mint
     *
     *  During the dao creation process important NFT's and tokens are minted.
     *  This contract makes sure they land in the correct DAO.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaDaoKey: Coll[Byte]              = _PAIDEIA_DAO_KEY
    val imPaideiaContractsProtoDao: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_PROTODAO
    val imPaideiaContractsDao: Coll[Byte]      = _IM_PAIDEIA_CONTRACTS_DAO

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val protoDAO: Box = INPUTS(0)
    val mint: Box      = SELF

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

    val daoOriginO: Box = OUTPUTS(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfigTree: AvlTree = paideiaConfig.R4[AvlTree].get

    val configTree: AvlTree = protoDAO.R4[AvlTree].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////
    
    val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](0).get
    val configProof: Coll[Byte]        = getVar[Coll[Byte]](1).get
    val mintedToken: Coll[Byte]        = getVar[Coll[Byte]](2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
        paideiaConfigTree.getMany(
            Coll(
                imPaideiaContractsProtoDao,
                imPaideiaContractsDao
            ),
            paideiaConfigProof
        )

    val protoDaoContracctHash: Coll[Byte] = bytearrayToContractHash(paideiaConfigValues(0))
    val daoOriginContractHash: Coll[Byte] = bytearrayToContractHash(paideiaConfigValues(1))

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            mintedToken
        ),
        configProof
    )

    val mintedTokenId: Coll[Byte] = bytearrayToTokenId(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctDataInput: Boolean = paideiaConfig.tokens(0)._1 == paideiaDaoKey

    val validProtoDAO: Boolean = 
        blake2b256(protoDAO.propositionBytes) == protoDaoContracctHash

    val validMintedToken: Boolean = mint.tokens(0)._1 == mintedTokenId

    val validDAOOutput: Boolean = 
        blake2b256(daoOriginO.propositionBytes) == daoOriginContractHash

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctDataInput,
        validProtoDAO,
        validMintedToken,
        validDAOOutput
    )))
}