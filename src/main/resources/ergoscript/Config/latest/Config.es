{
    // Get the Paideia DAO action token ID
    val daoActionTokenId = _IM_PAIDEIA_DAO_ACTION_TOKENID

    // Get the updated configuration box
    val updatedConfig = OUTPUTS(0)

    // Get the proof for the AVL+ tree associated with the configuration box
    val configProof = getVar[Coll[Byte]](0).get

    // Query the AVL+ tree for the values associated with IM_PAIDEIA_CONTRACTS_CONFIG key
    val config = updatedConfig.R4[AvlTree].get
    val configValues = config.getMany(Coll(
                _IM_PAIDEIA_CONTRACTS_CONFIG
            ),configProof)

    // Verify that essential properties of the input box are intact and unchanged
    val tokensIntact = SELF.tokens == updatedConfig.tokens
    val valueIntact = SELF.value <= updatedConfig.value
    val contractIntact = blake2b256(updatedConfig.propositionBytes) == configValues(0).get.slice(1,33)

    // Verify that one of the inputs contains the Paideia DAO action token
    val validAction = INPUTS.exists{
        (box: Box) =>
        if (box.tokens.size > 0) {
            box.tokens(0)._1 == daoActionTokenId
        } else {
            false
        }
    }

    // Compose all verification conditions into a single logical proposition
    sigmaProp(allOf(Coll(
        tokensIntact,
        valueIntact,
        contractIntact,
        validAction
    )))
}
