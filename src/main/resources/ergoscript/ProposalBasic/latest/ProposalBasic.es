{
    val proposalBasicInput = SELF

    val paideiaConfigInput = CONTEXT.dataInputs(0)

    val paideiaCorrectConfig = paideiaConfigInput.tokens(0)._1 == _PAIDEIA_DAO_KEY

    val paideiaConfigProof = getVar[Coll[Byte]](0).get

    val paideiaConfigValues = paideiaConfigInput.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_FEES_CREATE_PROPOSAL_PAIDEIA,
        _IM_PAIDEIA_CONTRACTS_TREASURY
    ),paideiaConfigProof)

    val configInput = CONTEXT.dataInputs(1)

    val correctConfig = configInput.tokens(0)._1 == _IM_PAIDEIA_DAO_KEY

    val configProof = getVar[Coll[Byte]](1).get

    val configValues = configInput.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_DAO_QUORUM,
        _IM_PAIDEIA_STAKING_STATE_TOKENID
    ),configProof)

    val quorumNeeded = byteArrayToLong(configValues(0).get.slice(1,9))

    val stakeInput = CONTEXT.dataInputs(2)

    val correctStakeInput = stakeInput.tokens(0)._1 == configValues(1).get.slice(6,38)

    val proposalBasicOutput = OUTPUTS(0)

    val validTransaction = if (proposalBasicInput.R6[Short].get != proposalBasicOutput.R6[Short].get && proposalBasicInput.R6[Short].get == 0) {

        val passed = if (proposalBasicInput.R5[Coll[Long]].get(1) > (stakeInput.R5[Coll[Long]].get(2)*quorumNeeded/1000)) 1.toShort else -1.toShort

        val correctOut = allOf(Coll(
            proposalBasicOutput.propositionBytes == proposalBasicInput.propositionBytes,
            proposalBasicOutput.value >= proposalBasicInput.value - 3000000L,
            proposalBasicOutput.tokens(0) == proposalBasicInput.tokens(0),
            proposalBasicOutput.R4[Int].get == proposalBasicInput.R4[Int].get,
            proposalBasicOutput.R5[Coll[Long]].get == proposalBasicInput.R5[Coll[Long]].get,
            proposalBasicOutput.R7[AvlTree].get == proposalBasicInput.R7[AvlTree].get,
            proposalBasicOutput.R6[Short].get == passed
        ))

        val treasuryOut = OUTPUTS(1)

        val padFee = byteArrayToLong(paideiaConfigValues(0).get.slice(1,9))
        val padTokens = if (SELF.tokens(1)._2 >= padFee) SELF.tokens(1)._2 else padFee

        val correctTreasuryOut = allOf(Coll(
            blake2b256(treasuryOut.propositionBytes) == paideiaConfigValues(1).get.slice(1,33),
            treasuryOut.value >= 1000000L,
            treasuryOut.tokens(0)._1 == _PAIDEIA_TOKEN_ID,
            treasuryOut.tokens(0)._2 >= padTokens
        ))

        val passedEnd = CONTEXT.preHeader.timestamp > proposalBasicInput.R5[Coll[Long]].get(0)

        allOf(Coll(
            correctOut,
            passedEnd,
            correctTreasuryOut
        ))
    } else {
        false
    }

    sigmaProp(allOf(Coll(
        paideiaCorrectConfig,
        correctConfig,
        correctStakeInput,
        validTransaction
    )))
}