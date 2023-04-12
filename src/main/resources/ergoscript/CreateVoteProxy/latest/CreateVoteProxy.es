{
    val configInput = CONTEXT.dataInputs(1)

    val correctConfig = configInput.tokens(0)._1 == _IM_PAIDEIA_DAO_KEY

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = configInput.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_DAO_NAME
    ),configProof)

    val daoName = configValues(0).get.slice(5,configValues(0).get.size)

    val daoOriginInput = INPUTS(0)
    val userOutput = OUTPUTS(2)
    val validUserOutput = allOf(Coll(
        userOutput.propositionBytes == SELF.R4[Coll[Byte]].get,
        userOutput.tokens(0)._1 == daoOriginInput.id,
        userOutput.tokens(0)._2 == 1,
        userOutput.R4[Coll[Byte]].get == daoName++_VOTE_KEY,
        userOutput.R5[Coll[Byte]].get == userOutput.R4[Coll[Byte]].get,
        userOutput.R6[Coll[Byte]].get == Coll(48.toByte)
    ))

    val oneKeyMinted = OUTPUTS.flatMap{(b: Box) => b.tokens}.fold(0L, {(z: Long, t: (Coll[Byte], Long)) => z + (if (t._1 == daoOriginInput.id) t._2 else 0L)}) == 1L

    sigmaProp(allOf(Coll(
        correctConfig,
        oneKeyMinted,
        validUserOutput
    )))
}