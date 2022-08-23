{
    val updatedConfig = OUTPUTS(0)

    val tokensIntact = SELF.tokens == updatedConfig.tokens
    val indexIntact = SELF.R4[Coll[Long]].get(0) == updatedConfig.R4[Coll[Long]].get(0)

    sigmaProp(allOf(Coll(
        tokensIntact,
        indexIntact
    )))
}