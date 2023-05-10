{
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

    val paideiaDaoKey: Coll[Byte]              = _PAIDEIA_DAO_KEY
    val imPaideiaContractsProtoDao: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_PROTODAO
    val imPaideiaContractsMint: Coll[Byte]     = _IM_PAIDEIA_CONTRACTS_MINT
    val imPaideiaDaoName: Coll[Byte]           = _IM_PAIDEIA_DAO_NAME
    val proposalText: Coll[Byte]               = _PROPOSAL
    val imPaideiaDaoActionTokenId: Coll[Byte]  = _IM_PAIDEIA_DAO_ACTION_TOKENID
    val actionText: Coll[Byte]                 = _ACTION
    val stakeStateText: Coll[Byte]             = _STAKE_STATE
    val imPaideiaContractsDao: Coll[Byte]      = _IM_PAIDEIA_CONTRACTS_DAO
    val imPaideiaDefaultConfig: Coll[Byte]     = _IM_PAIDEIA_DEFAULT_CONFIG
    val imPaideiaDefaultTreasury: Coll[Byte]   = _IM_PAIDEIA_DEFAULT_TREASURY
    val imPaideiaDaoKey: Coll[Byte]            = _IM_PAIDEIA_DAO_KEY
    val imPaideiaContractsTreasury: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_TREASURY
    val imPaideiaContractsConfig: Coll[Byte]   = _IM_PAIDEIA_CONTRACTS_CONFIG

    val imPaideiaDefaultConfigSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_CONFIG_SIGNATURE

    val imPaideiaDefaultTreasurySig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_TREASURY_SIGNATURE

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

    val configTree: AvlTree = protoDao.R4[AvlTree].get

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

        val configTreeO: AvlTree = protoDaoO.R4[AvlTree].get

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
            mintODecimals == decimals0
        ))

        val validProtoDAOOut = allOf(Coll(
            blake2b256(protoDaoO.propositionBytes) == protoDaoContractHash,
            protoDaoO.tokens == protoDao.tokens,
            configTreeO.digest == configTreeOut.digest
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
        * Makes sure the dao origin box and stake state box are created
        * correctly and the config has the correct values
        */

        ///////////////////////////////////////////////////////////////////////
        // Outputs                                                           //
        ///////////////////////////////////////////////////////////////////////

        val daoOriginO: Box = OUTPUTS(0)
        val configO: Box    = OUTPUTS(1)

        ///////////////////////////////////////////////////////////////////////
        // Registers                                                         //
        ///////////////////////////////////////////////////////////////////////

        val daoKeyO: Coll[Byte] = daoOriginO.R4[Coll[Byte]].get

        val configTreeO: AvlTree = configO.R4[AvlTree].get

        ///////////////////////////////////////////////////////////////////////
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val insertProof: Coll[Byte]        = getVar[Coll[Byte]](3).get
        val insertValues: Coll[Coll[Byte]] = getVar[Coll[Coll[Byte]]](4).get

        ///////////////////////////////////////////////////////////////////////
        // AVL Tree value extraction                                         //
        ///////////////////////////////////////////////////////////////////////

        val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
            paideiaConfigTree.getMany(
                Coll(
                    imPaideiaContractsDao,
                    imPaideiaDefaultConfig,
                    imPaideiaDefaultConfigSig,
                    imPaideiaDefaultTreasury,
                    imPaideiaDefaultTreasurySig
                ),
                paideiaConfigProof
            )

        val daoOriginContractHash: Coll[Byte] = 
            paideiaConfigValues(0).get.slice(1,33)

        val defaultConfigContract: Coll[Byte] = 
            paideiaConfigValues(1).get.slice(6,paideiaConfigValues(1).get.size)

        val defaultConfigContractSig: Coll[Byte] = paideiaConfigValues(2).get

        val defaultTreasuryContract: Coll[Byte] =
            paideiaConfigValues(3).get.slice(6,paideiaConfigValues(3).get.size)

        val defaultTreasuryContractSig: Coll[Byte] = paideiaConfigValues(4).get

        val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
            Coll(
                imPaideiaDaoProposalTokenId,
                imPaideiaDaoActionTokenId,
                imPaideiaDaoKey
            ),
            configProof
        )

        val proposalTokenId: Coll[Byte] = configValues(0).get.slice(6,38)
        val actionTokenId: Coll[Byte]   = configValues(1).get.slice(6,38)
        val daoKey: Coll[Byte]          = configValues(2).get.slice(6,38)

        ///////////////////////////////////////////////////////////////////////
        // Intermediate calculations                                         //
        ///////////////////////////////////////////////////////////////////////

        val treasuryContractSignature: Coll[Byte] = insertValues(0)
        val configContractSignature: Coll[Byte]   = insertValues(1)
        val treasuryContractHash: Coll[Byte]      = treasuryContractSignature.slice(1,33)
        val configContractHash: Coll[Byte]        = configContractSignature.slice(1,33)

        val finalConfig: AvlTree = configTree.insert(
            Coll(
                (imPaideiaContractsTreasury,treasuryContractSignature),
                (imPaideiaContractsConfig,configContractSignature)
            ),
            insertProof
        ).get

        val correctConfigContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultConfigContract,
                Coll(7),
                Coll(actionTokenId)
            )
        )

        val correctTreasuryContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultTreasuryContract,
                Coll(2),
                Coll(actionTokenId)
            )
        )

        ///////////////////////////////////////////////////////////////////////
        // Simple conditions                                                 //
        ///////////////////////////////////////////////////////////////////////

        val correctDAOOutput: Boolean = allOf(Coll(
            blake2b256(daoOriginO.propositionBytes) == daoOriginContractHash,
            daoOriginO.value >= 1000000L,
            daoOriginO.tokens(0) == protoDao.tokens(0),
            daoOriginO.tokens(1)._1 == proposalTokenId,
            daoOriginO.tokens(1)._2 == maxLong,
            daoOriginO.tokens(2)._1 == actionTokenId,
            daoOriginO.tokens(2)._2 == maxLong,
            daoOriginO.tokens.size == 3,
            daoKeyO == daoKey
        ))

        val correctConfigOutput: Boolean = allOf(Coll(
                blake2b256(configO.propositionBytes) == configContractHash,
                configO.value >= 1000000L,
                configO.tokens(0)._1 == daoKey,
                configO.tokens(0)._2 == 1L,
                configO.tokens.size == 1,
                configTreeO.digest == finalConfig.digest
            ))

        val correctContracts: Boolean = allOf(Coll(
            defaultConfigContractSig.patch(1,correctConfigContract,32) 
                == configContractSignature,
            defaultTreasuryContractSig.patch(1,correctTreasuryContract,32) 
                == treasuryContractSignature
        ))

        ///////////////////////////////////////////////////////////////////////
        // Transaction validity                                              //
        ///////////////////////////////////////////////////////////////////////

        allOf(Coll(
            correctContracts,
            correctDAOOutput,
            correctConfigOutput
        ))
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