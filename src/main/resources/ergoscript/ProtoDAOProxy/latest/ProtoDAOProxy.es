{
    val paideiaConfigBox = CONTEXT.dataInputs(0)
    val paideiaConfig = paideiaConfigBox.R4[AvlTree].get
    val paideiaConfigProof = getVar[Coll[Byte]](0).get
    val paideiaConfigValues = paideiaConfig.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_PROTODAO,
        _IM_PAIDEIA_CONTRACTS_MINT
    ),paideiaConfigProof)

    val correctDataInput = paideiaConfigBox.tokens(0)._1 == _PAIDEIA_DAO_KEY

    val emptyConfigDigest = _EMPTY_CONFIG_DIGEST
    val emptyConfig = getVar[AvlTree](1).get
    val validEmptyConfig = emptyConfigDigest == emptyConfig.digest
    val configInsertProof = getVar[Coll[Byte]](2).get
    val configValues = SELF.R4[Coll[Coll[Byte]]].get
    val filledOutConfig = emptyConfig.insert(Coll(
        (_IM_PAIDEIA_DAO_NAME,configValues(0)),
        (_IM_PAIDEIA_DAO_GOVERNANCE_TOKEN_ID,configValues(1)),
        (_IM_PAIDEIA_DAO_KEY,Coll(10.toByte,0.toByte,0.toByte,0.toByte,0.toByte,32.toByte) ++ SELF.id),
        (_IM_PAIDEIA_DAO_GOVERNANCE_TYPE,configValues(2)),
        (_IM_PAIDEIA_DAO_QUORUM,configValues(3)),
        (_IM_PAIDEIA_DAO_THRESHOLD,configValues(4)),
        (_IM_PAIDEIA_STAKING_EMISSION_AMOUNT,configValues(5)),
        (_IM_PAIDEIA_STAKING_EMISSION_DELAY,configValues(6)),
        (_IM_PAIDEIA_STAKING_CYCLE_LENGTH,configValues(7)),
        (_IM_PAIDEIA_STAKING_PROFITSHARE_PCT,configValues(8))
    ),configInsertProof).get

    val protoDAOOut = OUTPUTS(0)
    val mintOut = OUTPUTS(3)

    val validProtoDAOOut = allOf(Coll(
        validEmptyConfig,
        blake2b256(protoDAOOut.propositionBytes) == paideiaConfigValues(0).get.slice(1,33),
        protoDAOOut.R4[AvlTree].get.digest == filledOutConfig.digest
    ))

    val validMintOut = allOf(Coll(
        blake2b256(mintOut.propositionBytes) == paideiaConfigValues(1).get.slice(1,33),
        mintOut.tokens(0)._1 == SELF.id,
        mintOut.tokens(0)._2 == 1L,
        mintOut.R4[Coll[Byte]].get == configValues(0).slice(5,configValues(0).size)++_DAO_KEY,
        //Description is the same as token name
        mintOut.R5[Coll[Byte]].get == mintOut.R4[Coll[Byte]].get,
        mintOut.R6[Coll[Byte]].get == Coll(48.toByte)
    ))

    sigmaProp(validProtoDAOOut && validMintOut)
}