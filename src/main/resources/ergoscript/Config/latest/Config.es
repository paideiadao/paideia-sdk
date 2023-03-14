{
    val daoActionTokenId = _IM_PAIDEIA_DAO_ACTION_TOKENID

    val updatedConfig = OUTPUTS(0)

    val configProof = getVar[Coll[Byte]](0).get

    val config = updatedConfig.R4[AvlTree].get
    val configValues = config.getMany(Coll(
                _IM_PAIDEIA_CONTRACTS_CONFIG
            ),configProof)

    val tokensIntact = SELF.tokens == updatedConfig.tokens
    val valueIntact = SELF.value <= updatedConfig.value
    val contractIntact = blake2b256(updatedConfig.propositionBytes) == configValues(0).get.slice(1,33)

    val validAction = INPUTS.exists{
        (box: Box) =>
        if (box.tokens.size > 0) {
            box.tokens(0)._1 == daoActionTokenId
        } else {
            false
        }
    }

    sigmaProp(allOf(Coll(
        tokensIntact,
        valueIntact,
        contractIntact,
        validAction
    )))
}