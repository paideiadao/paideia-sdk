{
    val paideiaConfigBox = CONTEXT.dataInputs(0)
    val paideiaConfig = paideiaConfigBox.R4[AvlTree].get
    val paideiaConfigProof = getVar[Coll[Byte]](0).get
    val paideiaConfigValues = paideiaConfig.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_PROTODAO
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
        (_IM_PAIDEIA_DAO_KEY,Coll(10.toByte,0.toByte,0.toByte,0.toByte,0.toByte,32.toByte) ++ SELF.id)
    ),configInsertProof).get

    val protoDAOOut = OUTPUTS(0)

    val validProtoDAOOut = allOf(Coll(
        validEmptyConfig,
        blake2b256(protoDAOOut.propositionBytes) == paideiaConfigValues(0).get.slice(1,33),
        protoDAOOut.R4[AvlTree].get.digest == filledOutConfig.digest
    ))

    sigmaProp(validProtoDAOOut)
}