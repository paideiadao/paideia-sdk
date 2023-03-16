{
    val paideiaConfigBox = CONTEXT.dataInputs(0)
    val paideiaConfig = paideiaConfigBox.R4[AvlTree].get
    val paideiaConfigProof = getVar[Coll[Byte]](0).get
    val configValues = paideiaConfig.getMany(Coll(
        _IM_PAIDEIA_FEES_CREATEDAO_ERG,
        _IM_PAIDEIA_FEES_CREATEDAO_PAIDEIA,
        _IM_PAIDEIA_CONTRACTS_PROTODAO,
       _IM_PAIDEIA_CONTRACTS_PROTODAOPROXY,
       _IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT
    ),paideiaConfigProof)

    val correctDataInput = paideiaConfigBox.tokens(0)._1 == _PAIDEIA_DAO_KEY

    val createDAOFeeErg = byteArrayToLong(configValues(0).get.slice(1,9))
    val createDAOFeePaideia = byteArrayToLong(configValues(1).get.slice(1,9))

    val protoDAOContract = configValues(2).get.slice(1,33)
    val protoDAOProxyContract = configValues(3).get.slice(1,33)
    val paideiaProfitShareContract = configValues(4).get.slice(1,33)

    val originOut = OUTPUTS(1)
    val protoDAOOut = OUTPUTS(0)
    val paideiaProfitShareOut = OUTPUTS(2)
    val protoDAOProxyIn = INPUTS(0)

    val validProtoDAOCreation = allOf(Coll(
        originOut.tokens(0)._1 == SELF.tokens(0)._1,
        originOut.tokens(0)._2 == SELF.tokens(0)._2,
        originOut.tokens(1)._1 == SELF.tokens(1)._1,
        originOut.tokens(1)._2 == SELF.tokens(1)._2 - 1L,
        originOut.tokens.size == 2,
        originOut.propositionBytes == SELF.propositionBytes,
        blake2b256(protoDAOOut.propositionBytes) == protoDAOContract,
        protoDAOOut.tokens(0)._1 == SELF.tokens(1)._1,
        protoDAOOut.tokens(0)._2 == 1L,
        blake2b256(paideiaProfitShareOut.propositionBytes) == paideiaProfitShareContract,
        paideiaProfitShareOut.value == createDAOFeeErg + 1000000L,
        paideiaProfitShareOut.tokens(0)._1 == _PAIDEIA_TOKEN_ID,
        paideiaProfitShareOut.tokens(0)._2 == createDAOFeePaideia,
        blake2b256(protoDAOProxyIn.propositionBytes) == protoDAOProxyContract
    ))

    sigmaProp(validProtoDAOCreation)
}