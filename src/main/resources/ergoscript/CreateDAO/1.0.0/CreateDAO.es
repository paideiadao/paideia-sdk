/**
 * Ensures the DAO is created correctly
 *
 * @param paideiaDaoKey Token ID of the paideia dao key
 *
 * @return
 */
@contract def createDAO(paideiaDaoKey: Coll[Byte]) = {
    #import lib/maxLong/1.0.0/maxLong.es;
    #import lib/emptyDigest/1.0.0/emptyDigest.es;
    #import lib/config/1.0.0/config.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val avlTreeKeysHash: Coll[Byte] = _AVL_TREE_KEYS_HASH

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val createDao: Box = SELF
    val protoDao: Box  = INPUTS(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfig: Box = CONTEXT.dataInputs(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // OUTPUTS                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoOriginO: Box        = OUTPUTS(0)
    val configO: Box           = OUTPUTS(1)
    val stakeStateO: Box       = OUTPUTS(2)
    val stakeChangeO: Box      = OUTPUTS(3)
    val stakeStakeO: Box       = OUTPUTS(4)
    val stakeUnstakeO: Box     = OUTPUTS(5)
    val stakeCompoundO: Box    = OUTPUTS(6)
    val stakeSnapshotO: Box    = OUTPUTS(7)
    val stakeVoteO: Box        = OUTPUTS(8)
    val stakeProfitShareO: Box = OUTPUTS(9)
    val selfO: Box             = OUTPUTS(10)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val protoDaoKey: Coll[Byte]  = protoDao.R5[Coll[Byte]].get

    val configTreeO: AvlTree = configO.R4[AvlTree].get

    val stakeStateOTrees: Coll[AvlTree] = stakeStateO.R4[Coll[AvlTree]].get
    val stakeStateOR5: Coll[Long] = stakeStateO.R5[Coll[Long]].get
    val nextEmissionO: Long = stakeStateOR5(0)
    val restR5O: Coll[Long] = stakeStateOR5.slice(1, stakeStateOR5.size)
    val stakeStateOR6: Coll[Coll[Long]] = stakeStateO.R6[Coll[Coll[Long]]].get
    val stakeStateOR7: Coll[(AvlTree, AvlTree)] = stakeStateO.R7[Coll[(AvlTree, AvlTree)]].get
    val stakeStateOR8: Coll[Long] = stakeStateO.R8[Coll[Long]].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](0).get
    val configProof: Coll[Byte]        = getVar[Coll[Byte]](1).get

    val insertProof: Coll[Byte]        = getVar[Coll[Byte]](2).get
    val insertValues: Coll[Coll[Byte]] = getVar[Coll[Coll[Byte]]](3).get

    val actionProposalContracts: Coll[Coll[Byte]] = 
        getVar[Coll[Coll[Byte]]](4).get

    val avlTreeKeys: Coll[Coll[Coll[Byte]]] = getVar[Coll[Coll[Coll[Byte]]]](5).get
    val paideiaConfigKeys: Coll[Coll[Byte]] = avlTreeKeys(0)
    val configKeys: Coll[Coll[Byte]]        = avlTreeKeys(1)
    val insertKeys: Coll[Coll[Byte]]        = avlTreeKeys(2)
    
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////        

    val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
        configTree(paideiaConfig).getMany(
            paideiaConfigKeys,
            paideiaConfigProof
        )

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

    val defaultDaoContract: Coll[Byte] = paideiaConfigValues(0).get.slice(6,paideiaConfigValues(0).get.size)

    val defaultDaoSig: Coll[Byte] = paideiaConfigValues(27).get

    val configValues: Coll[Option[Coll[Byte]]] = configTree(protoDao).getMany(
        configKeys,
        configProof
    )

    val proposalTokenId: Coll[Byte]   = bytearrayToTokenId(configValues(0))
    val actionTokenId: Coll[Byte]     = bytearrayToTokenId(configValues(1))
    val daoKey: Coll[Byte]            = bytearrayToTokenId(configValues(2))
    val stakeStateTokenId: Coll[Byte] = bytearrayToTokenId(configValues(3))
    val cycleLength: Long             = 
        byteArrayToLong(configValues(4).get.slice(1,9))        

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

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
    val daoOriginSignature: Coll[Byte]          = insertValues(13)

    val actionSendFundsContract: Coll[Byte]    = actionProposalContracts(0)
    val actionUpdateConfigContract: Coll[Byte] = actionProposalContracts(1)
    val proposalBasicContract: Coll[Byte]      = actionProposalContracts(2)

    val stakingChangeHash: Coll[Byte]      = stakingChangeSignature.slice(1,33)
    val stakingStakeHash: Coll[Byte]       = stakingStakeSignature.slice(1,33)
    val stakingCompoundHash: Coll[Byte]    = stakingCompoundSignature.slice(1,33)
    val stakingProfitShareHash: Coll[Byte] = stakingProfitShareSignature.slice(1,33)
    val stakingSnapshotHash: Coll[Byte]    = stakingSnapshotSignature.slice(1,33)
    val stakingVoteHash: Coll[Byte]        = stakingVoteSignature.slice(1,33)
    val stakingUnstakeHash: Coll[Byte]     = stakingUnstakeSignature.slice(1,33)
    val daoOriginHash: Coll[Byte]          = daoOriginSignature.slice(1,33)

    val finalConfig: AvlTree = configTree(protoDao).insert(
        Coll(
            (insertKeys(0),treasuryContractSignature),
            (insertKeys(1),configContractSignature),
            (blake2b256(insertKeys(2)++actionSendFundsContract),actionSendFundsSignature),
            (blake2b256(insertKeys(2)++actionUpdateConfigContract),actionUpdateConfigSignature),
            (blake2b256(insertKeys(3)++proposalBasicContract),proposalBasicSignature),
            (insertKeys(4),stakingChangeSignature),
            (insertKeys(5),stakingStakeSignature),
            (insertKeys(6),stakingCompoundSignature),
            (insertKeys(7),stakingProfitShareSignature),
            (insertKeys(8),stakingSnapshotSignature),
            (insertKeys(9),stakingStateSignature),
            (insertKeys(10),stakingVoteSignature),
            (insertKeys(11),stakingUnstakeSignature),
            (insertKeys(12),daoOriginSignature)
        ),
        insertProof
    ).get

    val correctConfigContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultConfigContract,
            Coll(0),
            Coll(actionTokenId)
        )
    )

    val correctTreasuryContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultTreasuryContract,
            Coll(0,3),
            Coll(daoKey,actionTokenId++stakeStateTokenId)
        )
    )

    val correctActionSendFundsContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultActionSendFundsContract,
            Coll(0,1),
            Coll(daoKey,proposalTokenId)
        )
    )

    val correctActionUpdateConfigContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultActionUpdateConfigContract,
            Coll(0,1),
            Coll(daoKey,proposalTokenId)
        )
    )

    val correctProposalBasicContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultProposalBasicContract,
            Coll(0,3),
            Coll(daoKey,stakeStateTokenId)
        )
    )

    val correctStakingChangeContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultStakingChangeContract,
            Coll(0,1),
            Coll(daoKey,stakeStateTokenId)
        )
    )

    val correctStakingStakeContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultStakingStakeContract,
            Coll(0,1),
            Coll(daoKey,stakeStateTokenId)
        )
    )

    val correctStakingCompoundContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultStakingCompoundContract,
            Coll(0,1),
            Coll(daoKey,stakeStateTokenId)
        )
    )

    val correctStakingProfitShareContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultStakingProfitShareContract,
            Coll(0,1),
            Coll(daoKey,stakeStateTokenId)
        )
    )

    val correctStakingSnapshotContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultStakingSnapshotContract,
            Coll(0,1),
            Coll(daoKey,stakeStateTokenId)
        )
    )

    val correctStakingStateContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultStakingStateContract,
            Coll(0),
            Coll(daoKey)
        )
    )

    val correctStakingVoteContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultStakingVoteContract,
            Coll(0,1),
            Coll(daoKey,stakeStateTokenId)
        )
    )

    val correctStakingUnstakeContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultStakingUnstakeContract,
            Coll(0,1),
            Coll(daoKey,stakeStateTokenId)
        )
    )

    val correctDaoOriginContract: Coll[Byte] = blake2b256(
        substConstants(
            defaultDaoContract,
            Coll(0,3),
            Coll(daoKey, stakeStateTokenId)
        )
    )

    val currentTime: Long = CONTEXT.preHeader.timestamp
    
    val correctNextEmission: Long = currentTime + cycleLength

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctDAOOutput: Boolean = allOf(Coll(
        blake2b256(daoOriginO.propositionBytes) == daoOriginHash,
        daoOriginO.value >= 1000000L,
        daoOriginO.tokens(0) == protoDao.tokens(0),
        daoOriginO.tokens(1)._1 == proposalTokenId,
        daoOriginO.tokens(1)._2 == maxLong,
        daoOriginO.tokens(2)._1 == actionTokenId,
        daoOriginO.tokens(2)._2 == maxLong,
        daoOriginO.tokens.size == 3,
    ))

    val correctConfigOutput: Boolean = allOf(Coll(
        blake2b256(configO.propositionBytes) == configContractSignature.slice(1,33),
        configO.value >= 1000000L,
        configO.tokens(0)._1 == daoKey,
        configO.tokens(0)._2 == 1L,
        configO.tokens.size == 1,
        configTreeO.digest == finalConfig.digest
    ))

    val correctStakeStateOutput: Boolean = allOf(Coll(
        blake2b256(stakeStateO.propositionBytes) == stakingStateSignature.slice(1,33),
        stakeStateO.value >= 1000000L,
        stakeStateO.tokens(0)._1 == stakeStateTokenId,
        stakeStateO.tokens(1) == protoDao.tokens(1),
        stakeStateO.tokens.size == 2,
        stakeStateOTrees.forall{(t: AvlTree) => t.digest == emptyDigest},
        nextEmissionO >= correctNextEmission - 3600000L,
        nextEmissionO < correctNextEmission + 3600000L,
        restR5O.forall{(l: Long) => l == 0L},
        stakeStateOR6.flatMap{(cl: Coll[Long]) => cl}.forall{(l: Long) => l == 0L},
        stakeStateOR7.forall{(tt: (AvlTree, AvlTree)) => (tt._1.digest == emptyDigest && tt._2.digest == emptyDigest)},
        stakeStateOR8.forall{(l: Long) => l == 0L}
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
        defaultDaoSig.patch(1,correctDaoOriginContract,32) == daoOriginSignature
    ))

    val correctConfig: Boolean = paideiaConfig.tokens(0)._1 == paideiaDaoKey

    val correctDummyOutputs: Boolean = allOf(Coll(
        stakeChangeO.value >= 1000000L,
        blake2b256(stakeChangeO.propositionBytes) == stakingChangeHash,
        stakeStakeO.value >= 1000000L,
        blake2b256(stakeStakeO.propositionBytes) == stakingStakeHash,
        stakeUnstakeO.value >= 1000000L,
        blake2b256(stakeUnstakeO.propositionBytes) == stakingUnstakeHash,
        stakeCompoundO.value >= 1000000L,
        blake2b256(stakeCompoundO.propositionBytes) == stakingCompoundHash,
        stakeSnapshotO.value >= 1000000L,
        blake2b256(stakeSnapshotO.propositionBytes) == stakingSnapshotHash,
        stakeVoteO.value >= 1000000L,
        blake2b256(stakeVoteO.propositionBytes) == stakingVoteHash,
        stakeProfitShareO.value >= 1000000L,
        blake2b256(stakeProfitShareO.propositionBytes) == stakingProfitShareHash
    ))

    val correctSelfOut: Boolean = allOf(Coll(
        selfO.value >= createDao.value,
        selfO.propositionBytes == createDao.propositionBytes
    ))

    val correctAvlTreeKeys: Boolean = avlTreeKeysHash == blake2b256(avlTreeKeys.flatMap{(f: Coll[Coll[Byte]]) => f}.flatMap{(f: Coll[Byte]) => f})

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(
        Coll(
            correctAvlTreeKeys,
            correctConfig,
            correctContracts,
            correctDAOOutput,
            correctConfigOutput,
            correctStakeStateOutput,
            correctDummyOutputs,
            correctSelfOut
        )
    ))
}