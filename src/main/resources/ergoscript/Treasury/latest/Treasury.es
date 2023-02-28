{
    val daoActionTokenId = _IM_PAIDEIA_DAO_ACTION_TOKENID
    val validAction = INPUTS.exists{
        (box: Box) =>
        if (box.tokens.size > 0) {
            box.tokens(0)._1 == daoActionTokenId
        } else {
            false
        }
    }

    sigmaProp(validAction)
}