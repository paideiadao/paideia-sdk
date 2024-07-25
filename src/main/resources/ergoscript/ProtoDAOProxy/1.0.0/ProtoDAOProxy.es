/** 
 *  The contract is used by a user to initiate the dao creation process
 *  and ensure it uses the configurations requested by the user
 *
 * @param paideiaDaoKey Token ID of paideia dao key
 *
 * @return
 */
@contract def protoDAOProxy(paideiaDaoKey: Coll[Byte]) = {
    #import lib/validRefund/1.0.0/validRefund.es;
    #import lib/config/1.0.0/config.es;
    #import lib/box/1.0.0/box.es;

    // Refund logic
    sigmaProp(
    if (OUTPUTS.size == 2) {
      validRefund((SELF, (OUTPUTS(0), (SELF.R6[Coll[Byte]].get, 15))))
    } else {

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsProtoDao: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_PROTODAO
    val imPaideiaContractsMint: Coll[Byte]     = _IM_PAIDEIA_CONTRACTS_MINT
    val emptyConfigDigest: Coll[Byte]          = _EMPTY_CONFIG_DIGEST
    val imPaideiaDaoName: Coll[Byte]           = _IM_PAIDEIA_DAO_NAME
    val imPaideiaDaoKey: Coll[Byte]            = _IM_PAIDEIA_DAO_KEY
    val imPaideiaDaoGovernanceType: Coll[Byte] = _IM_PAIDEIA_DAO_GOVERNANCE_TYPE
    val imPaideiaDaoQuorum: Coll[Byte]         = _IM_PAIDEIA_DAO_QUORUM
    val imPaideiaDaoThreshold: Coll[Byte]      = _IM_PAIDEIA_DAO_THRESHOLD
    val daoKeyText: Coll[Byte]                 = _DAO_KEY

    val imPaideiaContractsPaideiaOrigin: Coll[Byte] =
      _IM_PAIDEIA_CONTRACTS_PAIDEIA_ORIGIN

    val imPaideiaStakingPureParticipationWeight: Coll[Byte] =
        _IM_PAIDEIA_STAKING_PUREPARTICIPATION_WEIGHT

    val imPaideiaStakingParticipationWeight: Coll[Byte] =
        _IM_PAIDEIA_STAKING_PARTICIPATION_WEIGHT

    val imPaideiaStakingProfitSharePct: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_PROFITSHARE_PCT

    val imPaideiaStakingCycleLength: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_CYCLE_LENGTH

    val imPaideiaStakingEmissionDelay: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_EMISSION_DELAY

    val imPaideiaStakingEmissionAmount: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_EMISSION_AMOUNT

    val imPaideiaDaoUrl: Coll[Byte] = 
      _IM_PAIDEIA_DAO_URL

    val imPaideiaDaoDescription: Coll[Byte] =
      _IM_PAIDEIA_DAO_DESCRIPTION

    val imPaideiaDaoLogo: Coll[Byte] =
      _IM_PAIDEIA_DAO_LOGO

    val imPaideiaDaoMinProposaltime: Coll[Byte] =
      _IM_PAIDEIA_DAO_MIN_PROPOSAL_TIME

    val imPaideiaDaoBanner: Coll[Byte] =
      _IM_PAIDEIA_DAO_BANNER

    val imPaideiaDaoBannerEnabled: Coll[Byte] =
      _IM_PAIDEIA_DAO_BANNER_ENABLED

    val imPaideiaDaoFooter: Coll[Byte] =
      _IM_PAIDEIA_DAO_FOOTER

    val imPaideiaDaoFooterEnabled: Coll[Byte] =
      _IM_PAIDEIA_DAO_FOOTER_ENABLED

    val imPaideiaDaoTheme: Coll[Byte] =
      _IM_PAIDEIA_DAO_THEME

    val collBPrefix: Coll[Byte] = 
        Coll(10.toByte,0.toByte,0.toByte,0.toByte,0.toByte,32.toByte)

    val imPaideiaDaoGovernanceTokenId: Coll[Byte] = 
        _IM_PAIDEIA_DAO_GOVERNANCE_TOKEN_ID

    val decimals0: Coll[Byte] =  Coll(48.toByte)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val protoDaoProxy: Box = SELF

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

    val protoDaoO: Box = OUTPUTS(0)
    val mintO: Box     = OUTPUTS(3)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Coll[Byte]] = protoDaoProxy.R4[Coll[Coll[Byte]]].get
    val stakePoolSize: Long            = protoDaoProxy.R5[Coll[Long]].get(0)

    val paideiaConfigTree: AvlTree = paideiaConfig.R4[AvlTree].get

    val mintOName: Coll[Byte]     = mintO.R4[Coll[Byte]].get
    val mintODesc: Coll[Byte]     = mintO.R5[Coll[Byte]].get
    val mintODecimals: Coll[Byte] = mintO.R6[Coll[Byte]].get

    val configTreeO: AvlTree = protoDaoO.R4[AvlTree].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](0).get
    val emptyConfig: AvlTree           = getVar[AvlTree](1).get
    val configInsertProof: Coll[Byte]  = getVar[Coll[Byte]](2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
        paideiaConfigTree.getMany(
            Coll(
                imPaideiaContractsProtoDao,
                imPaideiaContractsMint,
                imPaideiaContractsPaideiaOrigin
            ),
            paideiaConfigProof
        )

    val protoDaoContractHash: Coll[Byte] = 
      bytearrayToContractHash(paideiaConfigValues(0))

    val mintContractHash: Coll[Byte] = 
      bytearrayToContractHash(paideiaConfigValues(1))

    val paideiaOriginContractHash: Coll[Byte] = bytearrayToContractHash(paideiaConfigValues(2))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaOrigin: Box = filterByHash((INPUTS, paideiaOriginContractHash))(0)

    val daoKey: Coll[Byte] = protoDaoProxy.id

    val filledOutConfigTree: AvlTree = emptyConfig.insert(Coll(
        (imPaideiaDaoName, configValues(0)),
        (imPaideiaDaoGovernanceTokenId, configValues(1)),
        (imPaideiaDaoKey, collBPrefix ++ daoKey),
        (imPaideiaDaoGovernanceType,configValues(2)),
        (imPaideiaDaoQuorum,configValues(3)),
        (imPaideiaDaoThreshold,configValues(4)),
        (imPaideiaStakingEmissionAmount,configValues(5)),
        (imPaideiaStakingEmissionDelay,configValues(6)),
        (imPaideiaStakingCycleLength,configValues(7)),
        (imPaideiaStakingProfitSharePct,configValues(8)),
        (imPaideiaStakingPureParticipationWeight,configValues(9)),
        (imPaideiaStakingParticipationWeight,configValues(10)),
        (imPaideiaDaoUrl,configValues(11)),
        (imPaideiaDaoDescription,configValues(12)),
        (imPaideiaDaoLogo,configValues(13)),
        (imPaideiaDaoMinProposaltime,configValues(14)),
        (imPaideiaDaoBanner,configValues(15)),
        (imPaideiaDaoBannerEnabled,configValues(16)),
        (imPaideiaDaoFooter,configValues(17)),
        (imPaideiaDaoFooterEnabled,configValues(18)),
        (imPaideiaDaoTheme,configValues(19))
    ),configInsertProof).get

    val daoName: Coll[Byte] = configValues(0).slice(5, configValues(0).size)
    val daoGovernanceTokenId: Coll[Byte] = configValues(1).slice(6,38)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = paideiaConfig.tokens(0)._1 == paideiaDaoKey

    val validEmptyConfig: Boolean = emptyConfigDigest == emptyConfig.digest

    val validProtoDAOOut: Boolean = allOf(Coll(
        blake2b256(protoDaoO.propositionBytes) == protoDaoContractHash,
        configTreeO.digest == filledOutConfigTree.digest,
        protoDaoO.tokens(0)._1 == paideiaOrigin.tokens(1)._1,
        protoDaoO.tokens(1)._1 == daoGovernanceTokenId,
        protoDaoO.tokens(1)._2 == stakePoolSize + 1L,
        protoDaoO.value >= 13000000L
    ))

    val validMintOut = allOf(Coll(
        blake2b256(mintO.propositionBytes) == mintContractHash,
        mintO.tokens(0)._1 == daoKey,
        mintO.tokens(0)._2 == 1L,
        mintOName == daoName ++ daoKeyText,
        //Description is the same as token name
        mintODesc == mintOName,
        mintODecimals == decimals0
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    allOf(
        Coll(
            correctConfig,
            validEmptyConfig,
            validProtoDAOOut,
            validMintOut
        )
    )})
}