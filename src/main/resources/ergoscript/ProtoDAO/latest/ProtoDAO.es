/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def protoDAO(paideiaDaoKey: Coll[Byte]) = {
    /**
     *
     *  ProtoDAO
     *
     *  Some actions need to be taken before a DAO can be started. This contract
     *  ensures this is done correctly
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsProtoDao: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_PROTODAO
    val imPaideiaContractsMint: Coll[Byte]     = _IM_PAIDEIA_CONTRACTS_MINT
    val imPaideiaDaoName: Coll[Byte]           = _IM_PAIDEIA_DAO_NAME
    val proposalText: Coll[Byte]               = _PROPOSAL
    val imPaideiaDaoActionTokenId: Coll[Byte]  = _IM_PAIDEIA_DAO_ACTION_TOKENID
    val actionText: Coll[Byte]                 = _ACTION
    val stakeStateText: Coll[Byte]             = _STAKE_STATE
    val imPaideiaContractsDao: Coll[Byte]      = _IM_PAIDEIA_CONTRACTS_DAO
    val imPaideiaDaoKey: Coll[Byte]            = _IM_PAIDEIA_DAO_KEY

    val imPaideiaContractsCreateDao: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_CREATE_DAO

    val imPaideiaStakingStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKENID

    val imPaideiaDaoProposalTokenId: Coll[Byte] = 
        _IM_PAIDEIA_DAO_PROPOSAL_TOKENID

    val maxLong: Long = 9223372036854775807L

    val mintTransaction: Byte      = 0.toByte
    val createDaoTransaction: Byte = 1.toByte

    val decimals0: Coll[Byte] = Coll(48.toByte)

    val collBytePrefix: Coll[Byte] = 
        Coll(10.toByte,0.toByte,0.toByte,0.toByte,0.toByte,32.toByte)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val protoDao: Box = SELF

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfig: Box = CONTEXT.dataInputs(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree      = protoDao.R4[AvlTree].get
    val protoDaoKey: Coll[Byte]  = protoDao.R5[Coll[Byte]].get

    val paideiaConfigTree: AvlTree = paideiaConfig.R4[AvlTree].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val transactionType: Byte          = getVar[Byte](0).get
    val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](1).get
    val configProof: Coll[Byte]        = getVar[Coll[Byte]](2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Transaction dependent logic                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val validTransaction = if (transactionType == mintTransaction) {

        /**
        * Mint Transaction
        * Mint a token to be used in the DAO
        */

        ///////////////////////////////////////////////////////////////////////
        // Outputs                                                           //
        ///////////////////////////////////////////////////////////////////////

        val protoDaoO: Box = OUTPUTS(0)
        val mintO: Box     = OUTPUTS(1)

        ///////////////////////////////////////////////////////////////////////
        // Registers                                                         //
        ///////////////////////////////////////////////////////////////////////

        val configTreeO: AvlTree     = protoDaoO.R4[AvlTree].get
        val protoDaoOKey: Coll[Byte] = protoDaoO.R5[Coll[Byte]].get

        val mintOName: Coll[Byte]     = mintO.R4[Coll[Byte]].get
        val mintODesc: Coll[Byte]     = mintO.R5[Coll[Byte]].get
        val mintODecimals: Coll[Byte] = mintO.R6[Coll[Byte]].get

        ///////////////////////////////////////////////////////////////////////
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val mintAction: Coll[Byte]        = getVar[Coll[Byte]](3).get
        val configInsertProof: Coll[Byte] = getVar[Coll[Byte]](4).get

        ///////////////////////////////////////////////////////////////////////
        // AVL Tree value extraction                                         //
        ///////////////////////////////////////////////////////////////////////

        val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
            paideiaConfigTree.getMany(
                Coll(
                    imPaideiaContractsProtoDao,
                    imPaideiaContractsMint
                ),
                paideiaConfigProof
            )

        val protoDaoContractHash: Coll[Byte] = paideiaConfigValues(0).get.slice(1,33)
        val mintContractHash: Coll[Byte]     = paideiaConfigValues(1).get.slice(1,33)

        val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
            Coll(
                imPaideiaDaoName  
            ),
            configProof
        )

        val daoName: Coll[Byte] = 
            configValues(0).get.slice(5,configValues(0).get.size)

        ///////////////////////////////////////////////////////////////////////
        // Intermediate calculations                                         //
        ///////////////////////////////////////////////////////////////////////

        val configTreeOut: AvlTree = configTree.insert(
            Coll(
                (
                    mintAction,
                    collBytePrefix ++ protoDao.id)
            ),
            configInsertProof
        ).get

        val mintInfo: (Coll[Byte], Long) = 
            if (mintAction == imPaideiaDaoProposalTokenId) {
                (daoName++proposalText,maxLong)
            } else {
                if (mintAction == imPaideiaDaoActionTokenId) {
                    (daoName++actionText,maxLong)
                } else {
                    if (mintAction == imPaideiaStakingStateTokenId) {
                        (daoName++stakeStateText,1L)
                    } else {
                        (daoName,-1L)
                    }
                }
            }

        ///////////////////////////////////////////////////////////////////////
        // Simple conditions                                                 //
        ///////////////////////////////////////////////////////////////////////

        val validMint: Boolean = allOf(Coll(
            blake2b256(mintO.propositionBytes) == mintContractHash,
            mintO.tokens(0)._1 == SELF.id,
            mintO.tokens(0)._2 == mintInfo._2,
            mintOName == mintInfo._1,
            mintODesc == mintOName,
            mintODecimals == decimals0,
            mintO.value >= 1000000L
        ))

        val validProtoDAOOut = allOf(Coll(
            blake2b256(protoDaoO.propositionBytes) == protoDaoContractHash,
            protoDaoO.tokens == protoDao.tokens,
            configTreeO.digest == configTreeOut.digest,
            protoDaoO.value >= protoDao.value - 2000000L,
            protoDaoOKey == protoDaoKey
        ))

        ///////////////////////////////////////////////////////////////////////
        // Transaction validity                                              //
        ///////////////////////////////////////////////////////////////////////

        allOf(Coll(
            validMint,
            validProtoDAOOut
        ))

    } else {
        /**
        * Create DAO Transaction
        * Logic moved to separate contract
        */

        ///////////////////////////////////////////////////////////////////////
        // Inputs                                                            //
        ///////////////////////////////////////////////////////////////////////

        val createDao: Box = INPUTS(1)

        ///////////////////////////////////////////////////////////////////////
        // AVL Tree value extraction                                         //
        ///////////////////////////////////////////////////////////////////////

        val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
            paideiaConfigTree.getMany(
                Coll(
                    imPaideiaContractsCreateDao
                ),
                paideiaConfigProof
            )

        val createDaoHash: Coll[Byte] = paideiaConfigValues(0).get.slice(1,33)

        blake2b256(createDao.propositionBytes) == createDaoHash        
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = paideiaConfig.tokens(0)._1 == paideiaDaoKey

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(
        Coll(
            correctConfig,
            validTransaction
        )
    ))
}