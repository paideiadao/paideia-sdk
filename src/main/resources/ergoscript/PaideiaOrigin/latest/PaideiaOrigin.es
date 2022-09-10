{
    val paideiaConfigTokenId = _paideiaConfigTokenId
    val paideiaFeeConfig = CONTEXT.dataInputs(0)
    val paideiaContractsConfig = CONTEXT.dataInputs(1)

    val correctFeeDataInput = allOf(Coll(
        paideiaFeeConfig.tokens(0)._1 == paideiaConfigTokenId,
        paideiaFeeConfig.R4[Coll[Long]].get(0) == 11L
    ))

    val correctContractsDataInput = allOf(Coll(
        paideiaContractsConfig.tokens(0)._1 == paideiaConfigTokenId,
        paideiaContractsConfig.R4[Coll[Long]].get(0) == 12L
    ))

    val profitSharePct = paideiaFeeConfig.R4[Coll[Long]].get(1)

    val createDAOFee = paideiaFeeConfig.R5[Coll[(Long,Long)]].get(0)
    val createDAOFeeErg = createDAOFee._1
    val createDAOFeePaideia = createDAOFee._2

    val paideiaContracts = paideiaContractsConfig.R5[Coll[(Coll[Byte],(Coll[Byte],Coll[Byte]))]].get
    val protoDAOContract = paideiaContracts(0)._2._2
    val protoDAOProxyContract = paideiaContracts(1)._2._2
    val paideiaTreasuryContract = paideiaContracts(2)._2._2
    val paideiaProfitShareContract = paideiaContracts(3)._2._2

    val originOut = OUTPUTS(0)
    val protoDAOOut = OUTPUTS(1)
    val paideiaTreasuryOut = OUTPUTS(2)
    val paideiaProfitShareOut = OUTPUTS(3)
    val protoDAOProxyIn = INPUTS(1)

    val validProtoDAOCreation = allOf(Coll(
        originOut.tokens(0)._1 == SELF.tokens(0)._1,
        originOut.tokens(0)._2 == SELF.tokens(0)._2 - 1L,
        originOut.propositionBytes == SELF.propositionBytes,
        blake2b256(protoDAOOut.propositionBytes) == protoDAOContract,
        protoDAOOut.tokens(0)._1 == SELF.tokens(0)._1,
        protoDAOOut.tokens(1)._2 == 1L,
        paideiaTreasuryOut
    ))
}