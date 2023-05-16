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

    val imPaideiaDefaultActionSendFunds: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_ACTION_SEND_FUNDS
    val imPaideiaDefaultActionSendFundsSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_ACTION_SEND_FUNDS_SIG
    val imPaideiaDefaultActionUpdateConfig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_ACTION_UPDATE_CONFIG
    val imPaideiaDefaultActionUpdateConfigSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_ACTION_UPDATE_CONFIG_SIG
    val imPaideiaDefaultProposalBasic: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_PROPOSAL_BASIC
    val imPaideiaDefaultProposalBasicSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_PROPOSAL_BASIC_SIG
    val imPaideiaDefaultStakingChange: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_CHANGE
    val imPaideiaDefaultStakingChangeSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_CHANGE_SIG
    val imPaideiaDefaultStakingStake: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKE_STAKE
    val imPaideiaDefaultStakingStakeSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_STAKE_SIG
    val imPaideiaDefaultStakingCompound: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_COMPOUND
    val imPaideiaDefaultStakingCompoundSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_COMPOUND_SIG
    val imPaideiaDefaultStakingProfitShare: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_PROFITSHARE
    val imPaideiaDefaultStakingProfitShareSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_PROFITSHARE_SIG
    val imPaideiaDefaultStakingSnapshot: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_SNAPSHOT
    val imPaideiaDefaultStakingSnapshotSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_SNAPSHOT_SIG
    val imPaideiaDefaultStakingState: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_STATE
    val imPaideiaDefaultStakingStateSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_STATE_SIG
    val imPaideiaDefaultStakingVote: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_VOTE
    val imPaideiaDefaultStakingVoteSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_VOTE_SIG
    val imPaideiaDefaultStakingUnstake: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_UNSTAKE
    val imPaideiaDefaultStakingUnstakeSig: Coll[Byte] = 
        _IM_PAIDEIA_DEFAULT_STAKING_UNSTAKE_SIG

    val imPaideiaAction: Coll[Byte] = 
        _IM_PAIDEIA_ACTION
    val imPaideiaProposal: Coll[Byte] = 
        _IM_PAIDEIA_PROPOSAL
    val imPaideiaContractsStakingChange: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_CHANGE
    val imPaideiaContractsStakingStake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_STAKE
    val imPaideiaContractsStakingCompound: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND
    val imPaideiaContractsStakingProfitShare: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_PROFIT_SHARE
    val imPaideiaContractsStakingSnapshot: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT
    val imPaideiaContractsStakingState: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_STATE
    val imPaideiaContractsStakingVote: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_VOTE
    val imPaideiaContractsStakingUnstake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE

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
            mintODecimals == decimals0
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

        val actionProposalContracts: Coll[Coll[Byte]] = 
            getVar[Coll[Coll[Byte]]](5).get

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
                    imPaideiaDefaultTreasurySig,
                    imPaideiaDefaultActionSendFunds,
                    imPaideiaDefaultActionSendFundsSig,
                    imPaideiaDefaultActionUpdateConfig,
                    imPaideiaDefaultActionUpdateConfigSig,
                    imPaideiaDefaultProposalBasic,
                    imPaideiaDefaultProposalBasicSig,
                    imPaideiaDefaultStakingChange,
                    imPaideiaDefaultStakingChangeSig,
                    imPaideiaDefaultStakingStake,
                    imPaideiaDefaultStakingStakeSig,
                    imPaideiaDefaultStakingCompound,
                    imPaideiaDefaultStakingCompoundSig,
                    imPaideiaDefaultStakingProfitShare,
                    imPaideiaDefaultStakingProfitShareSig,
                    imPaideiaDefaultStakingSnapshot,
                    imPaideiaDefaultStakingSnapshotSig,
                    imPaideiaDefaultStakingState,
                    imPaideiaDefaultStakingStateSig,
                    imPaideiaDefaultStakingVote,
                    imPaideiaDefaultStakingVoteSig,
                    imPaideiaDefaultStakingUnstake,
                    imPaideiaDefaultStakingUnstakeSig
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

        val defaultActionSendFundsContract: Coll[Byte] =
            paideiaConfigValues(5).get.slice(6,paideiaConfigValues(5).get.size)

        val defaultActionSendFundsSig: Coll[Byte] = paideiaConfigValues(6).get

        val defaultActionUpdateConfigContract: Coll[Byte] =
            paideiaConfigValues(7).get.slice(6,paideiaConfigValues(7).get.size)

        val defaultActionUpdateConfigSig: Coll[Byte] = paideiaConfigValues(8).get

        val defaultProposalBasicContract: Coll[Byte] =
            paideiaConfigValues(9).get.slice(6,paideiaConfigValues(9).get.size)

        val defaultProposalBasicSig: Coll[Byte] = paideiaConfigValues(10).get

        val defaultStakingChangeContract: Coll[Byte] = 
            paideiaConfigValues(11).get.slice(6,paideiaConfigValues(11).get.size)

        val defaultStakingChangeSig: Coll[Byte] = paideiaConfigValues(12).get

        val defaultStakingStakeContract: Coll[Byte] =
            paideiaConfigValues(13).get.slice(6,paideiaConfigValues(13).get.size)

        val defaultStakingStakeSig: Coll[Byte] = paideiaConfigValues(14).get

        val defaultStakingCompoundContract: Coll[Byte] = 
            paideiaConfigValues(15).get.slice(6,paideiaConfigValues(15).get.size)

        val defaultStakingCompoundSig: Coll[Byte] = paideiaConfigValues(16).get

        val defaultStakingProfitShareContract: Coll[Byte] =
            paideiaConfigValues(17).get.slice(6,paideiaConfigValues(17).get.size)

        val defaultStakingProfitShareSig: Coll[Byte] = paideiaConfigValues(18).get

        val defaultStakingSnapshotContract: Coll[Byte] = 
            paideiaConfigValues(19).get.slice(6,paideiaConfigValues(19).get.size)

        val defaultStakingSnapshotSig: Coll[Byte] = paideiaConfigValues(20).get

        val defaultStakingStateContract: Coll[Byte] =
            paideiaConfigValues(21).get.slice(6,paideiaConfigValues(21).get.size)

        val defaultStakingStateSig: Coll[Byte] = paideiaConfigValues(22).get

        val defaultStakingVoteContract: Coll[Byte] = 
            paideiaConfigValues(23).get.slice(6,paideiaConfigValues(23).get.size)

        val defaultStakingVoteSig: Coll[Byte] = paideiaConfigValues(24).get

        val defaultStakingUnstakeContract: Coll[Byte] =
            paideiaConfigValues(25).get.slice(6,paideiaConfigValues(25).get.size)

        val defaultStakingUnstakeSig: Coll[Byte] = paideiaConfigValues(26).get

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

        val treasuryContractSignature: Coll[Byte]   = insertValues(0)
        val configContractSignature: Coll[Byte]     = insertValues(1)
        val actionSendFundsSignature: Coll[Byte]    = insertValues(2)
        val actionUpdateConfigSignature: Coll[Byte] = insertValues(3)
        val proposalBasicSignature: Coll[Byte]      = insertValues(4)
        val stakingChangeSignature: Coll[Byte]      = insertValues(5)
        val stakingStakeSignature: Coll[Byte]       = insertValues(6)
        val stakingCompoundSignature: Coll[Byte]    = insertValues(7)
        val stakingProfitShareSignature: Coll[Byte] = insertValues(8)
        val stakingSnapshotSignature: Coll[Byte]    = insertValues(9)
        val stakingStateSignature: Coll[Byte]       = insertValues(10)
        val stakingVoteSignature: Coll[Byte]        = insertValues(11)
        val stakingUnstakeSignature: Coll[Byte]     = insertValues(12)

        val actionSendFundsContract: Coll[Byte]    = actionProposalContracts(0)
        val actionUpdateConfigContract: Coll[Byte] = actionProposalContracts(1)
        val proposalBasicContract: Coll[Byte]      = actionProposalContracts(2)

        val treasuryContractHash: Coll[Byte]   = treasuryContractSignature.slice(1,33)
        val configContractHash: Coll[Byte]     = configContractSignature.slice(1,33)
        val actionSendFundsHash: Coll[Byte]    = actionSendFundsSignature.slice(1,33)
        val actionUpdateConfigHash: Coll[Byte] = actionUpdateConfigSignature.slice(1,33)
        val proposalBasicHash: Coll[Byte]      = proposalBasicSignature.slice(1,33)
        val stakingChangeHash: Coll[Byte]      = stakingChangeSignature.slice(1,33)
        val stakingStakeHash: Coll[Byte]       = stakingStakeSignature.slice(1,33)
        val stakingCompoundHash: Coll[Byte]    = stakingCompoundSignature.slice(1,33)
        val stakingProfitShareHash: Coll[Byte] = stakingProfitShareSignature.slice(1,33)
        val stakingSnapshotHash: Coll[Byte]    = stakingSnapshotSignature.slice(1,33)
        val stakingStateHash: Coll[Byte]       = stakingStateSignature.slice(1,33)
        val stakingVoteHash: Coll[Byte]        = stakingVoteSignature.slice(1,33)
        val stakingUnstakeHash: Coll[Byte]     = stakingUnstakeSignature.slice(1,33)

        val finalConfig: AvlTree = configTree.insert(
            Coll(
                (imPaideiaContractsTreasury,treasuryContractSignature),
                (imPaideiaContractsConfig,configContractSignature),
                (blake2b256(imPaideiaAction++actionSendFundsContract),actionSendFundsSignature),
                (blake2b256(imPaideiaAction++actionUpdateConfigContract),actionUpdateConfigSignature),
                (blake2b256(imPaideiaProposal++proposalBasicContract),proposalBasicSignature),
                (imPaideiaContractsStakingChange,stakingChangeSignature),
                (imPaideiaContractsStakingStake,stakingStakeSignature),
                (imPaideiaContractsStakingCompound,stakingCompoundSignature),
                (imPaideiaContractsStakingProfitShare,stakingProfitShareSignature),
                (imPaideiaContractsStakingSnapshot,stakingSnapshotSignature),
                (imPaideiaContractsStakingState,stakingStateSignature),
                (imPaideiaContractsStakingVote,stakingVoteSignature),
                (imPaideiaContractsStakingUnstake,stakingUnstakeSignature)
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

        val correctActionSendFundsContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultActionSendFundsContract,
                Coll(16,18),
                Coll(daoKey,proposalTokenId)
            )
        )

        val correctActionUpdateConfigContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultActionUpdateConfigContract,
                Coll(7,9),
                Coll(daoKey,proposalTokenId)
            )
        )

        val correctProposalBasicContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultProposalBasicContract,
                Coll(7),
                Coll(daoKey)
            )
        )

        val correctStakingChangeContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultStakingChangeContract,
                Coll(31),
                Coll(daoKey)
            )
        )

        val correctStakingStakeContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultStakingStakeContract,
                Coll(28),
                Coll(daoKey)
            )
        )

        val correctStakingCompoundContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultStakingCompoundContract,
                Coll(64),
                Coll(daoKey)
            )
        )

        val correctStakingProfitShareContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultStakingProfitShareContract,
                Coll(24),
                Coll(daoKey)
            )
        )

        val correctStakingSnapshotContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultStakingSnapshotContract,
                Coll(135),
                Coll(daoKey)
            )
        )

        val correctStakingStateContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultStakingStateContract,
                Coll(12),
                Coll(daoKey)
            )
        )

        val correctStakingVoteContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultStakingVoteContract,
                Coll(63),
                Coll(daoKey)
            )
        )

        val correctStakingUnstakeContract: Coll[Byte] = blake2b256(
            substConstants(
                defaultStakingUnstakeContract,
                Coll(27),
                Coll(daoKey)
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
                == treasuryContractSignature,
            defaultActionSendFundsSig.patch(1,correctActionSendFundsContract,32)
                == actionSendFundsSignature,
            defaultActionUpdateConfigSig.patch(1,correctActionUpdateConfigContract,32)
                == actionUpdateConfigSignature,
            defaultProposalBasicSig.patch(1,correctProposalBasicContract,32)
                == proposalBasicSignature,
            defaultStakingChangeSig.patch(1,correctStakingChangeContract,32)
                == stakingChangeSignature,
            defaultStakingCompoundSig.patch(1,correctStakingCompoundContract,32)
                == stakingCompoundSignature,
            defaultStakingProfitShareSig.patch(1,correctStakingProfitShareContract,32)
                == stakingProfitShareSignature,
            defaultStakingSnapshotSig.patch(1,correctStakingSnapshotContract,32)
                == stakingSnapshotSignature,
            defaultStakingStakeSig.patch(1,correctStakingStakeContract,32)
                == stakingStakeSignature,
            defaultStakingStateSig.patch(1,correctStakingStateContract,32)
                == stakingStateSignature,
            defaultStakingUnstakeSig.patch(1,correctStakingUnstakeContract,32)
                == stakingUnstakeSignature,
            defaultStakingVoteSig.patch(1,correctStakingVoteContract,32)
                == stakingVoteSignature,
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