{
    val maxLong = 9223372036854775807L
    val paideiaConfigBox = CONTEXT.dataInputs(0)
    val paideiaConfig = paideiaConfigBox.R4[AvlTree].get
    val paideiaConfigProof = getVar[Coll[Byte]](0).get
    val paideiaConfigValues = paideiaConfig.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_PROTODAO,
        _IM_PAIDEIA_CONTRACTS_MINT
    ),paideiaConfigProof)

    val correctDataInput = paideiaConfigBox.tokens(0)._1 == _PAIDEIA_DAO_KEY

    val mintAction = getVar[Coll[Byte]](1).get

    val configProof = getVar[Coll[Byte]](2).get
    val config = SELF.R4[AvlTree].get
    val configValues = config.getMany(Coll(
        _IM_PAIDEIA_DAO_NAME
    ),configProof)

    val configInsertProof = getVar[Coll[Byte]](3).get
    
    val configOut = config.insert(Coll(
        (mintAction,Coll(10.toByte,0.toByte,0.toByte,0.toByte,0.toByte,32.toByte) ++ SELF.id)
    ),configInsertProof).get

    val mintOut = OUTPUTS(1)
    val daoName = configValues(0).get.slice(5,configValues(0).get.size)

    val mintInfo = if (mintAction == _IM_PAIDEIA_DAO_VOTE_TOKENID) {
        (daoName++_VOTE,maxLong)
    } else {
        if (mintAction == _IM_PAIDEIA_DAO_PROPOSAL_TOKENID) {
            (daoName++_PROPOSAL,maxLong)
        } else {
            if (mintAction == _IM_PAIDEIA_DAO_ACTION_TOKENID) {
                (daoName++_ACTION,maxLong)
            } else {
                (daoName,-1L)
            }
        }
    }

    val validMint = allOf(Coll(
        blake2b256(mintOut.propositionBytes) == paideiaConfigValues(1).get.slice(1,33),
        mintOut.tokens(0)._1 == SELF.id,
        mintOut.tokens(0)._2 == mintInfo._2,
        mintOut.R4[Coll[Byte]].get == mintInfo._1,
        mintOut.R5[Coll[Byte]].get == mintOut.R4[Coll[Byte]].get,
        mintOut.R6[Coll[Byte]].get == Coll(48.toByte)
    ))

    val protoDAOOut = OUTPUTS(0)

    val validProtoDAOOut = allOf(Coll(
        blake2b256(protoDAOOut.propositionBytes) == paideiaConfigValues(0).get.slice(1,33),
        protoDAOOut.tokens == SELF.tokens,
        protoDAOOut.R4[AvlTree].get.digest == configOut.digest
    ))

    sigmaProp(correctDataInput && validMint && validProtoDAOOut)
}