{
    val paideiaConfigBox = CONTEXT.dataInputs(0)
    val paideiaConfig = paideiaConfigBox.R4[AvlTree].get
    val paideiaConfigProof = getVar[Coll[Byte]](0).get

    val correctDataInput = paideiaConfigBox.tokens(0)._1 == _PAIDEIA_DAO_KEY

    val paideiaConfigValues = paideiaConfig.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_PROTODAO,
        _IM_PAIDEIA_CONTRACTS_DAO
    ),paideiaConfigProof)

    val protoDAOInput = INPUTS(0)

    val config = protoDAOInput.R4[AvlTree].get

    val configProof = getVar[Coll[Byte]](1).get

    val mintedToken = getVar[Coll[Byte]](2).get

    val configValues = config.getMany(Coll(
        mintedToken
    ),configProof)

    val validProtoDAOInput = blake2b256(protoDAOInput.propositionBytes) == paideiaConfigValues(0).get.slice(1,33)

    val validMintedToken = SELF.tokens(0)._1 == configValues(0).get.slice(6,38)

    val daoOutput = OUTPUTS(0)

    val validDAOOutput = blake2b256(daoOutput.propositionBytes) == paideiaConfigValues(1).get.slice(1,33)

    sigmaProp(allOf(Coll(
        correctDataInput,
        validProtoDAOInput,
        validMintedToken,
        validDAOOutput
    )))
}