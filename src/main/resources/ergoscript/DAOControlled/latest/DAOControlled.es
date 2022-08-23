{
    val configIndex = _configIndex
    val configTokenId = _configTokenId
    
    val config = CONTEXT.dataInputs(0)

    val correctConfigTokenId = config.tokens(0)._1 == configTokenId
    val correctConfigIndex = config.R4[Coll[Long]].get(0) == configIndex

    val validTransaction = _script

    sigmaProp(allOf(Coll(
        validTransaction,
        correctConfigTokenId,
        correctConfigIndex
    )))
}