{
    val daoInput = SELF

    val paideiaConfigInput = CONTEXT.dataInputs(0)

    val paideiaCorrectConfig = paideiaConfigInput.tokens(0)._1 == _PAIDEIA_DAO_KEY

    val paideiaConfigProof = getVar[Coll[Byte]](0).get

    val paideiaConfigValues = paideiaConfigInput.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_DAO
    ),paideiaConfigProof)

    val configInput = CONTEXT.dataInputs(1)

    val correctConfig = configInput.tokens(0)._1 == daoInput.R4[Coll[Byte]].get

    val configProof = getVar[Coll[Byte]](1).get

    val configValues = configInput.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_VOTE,
        _IM_PAIDEIA_STAKING_STATE_TOKENID
    ),configProof)

    val daoOutput = OUTPUTS(0)

    val validTransaction = if (daoInput.tokens(2)._2-1 == daoOutput.tokens(2)._2) {
        val stakeInput = CONTEXT.dataInputs(2)

        val correctStakeInput = stakeInput.tokens(0)._1 == configValues(1).get.slice(6,38)

        val stakeProof = getVar[Coll[Byte]](2).get

        val voteOutput = OUTPUTS(1)

        val stake = stakeInput.R4[AvlTree].get.get(voteOutput.tokens(1)._1, stakeProof).get

        val correctStake = stake.size > 0

        val correctDAOOutput = allOf(Coll(
            blake2b256(daoOutput.propositionBytes) == paideiaConfigValues(0).get.slice(1,33),
            daoOutput.value >= daoInput.value,
            daoOutput.tokens(0) == daoInput.tokens(0),
            daoOutput.tokens(1) == daoInput.tokens(1),
            daoOutput.tokens(2)._1 == daoInput.tokens(2)._1,
            daoOutput.tokens(3) == daoInput.tokens(3),
            daoOutput.tokens.size == 4
        ))

        val correctVoteOutput = allOf(Coll(
            blake2b256(voteOutput.propositionBytes) == configValues(0).get.slice(1,33),
            voteOutput.value == 1000000L,
            voteOutput.tokens(0)._1 == daoInput.tokens(2)._1,
            voteOutput.tokens(0)._2 == 1L
        ))

        allOf(Coll(
            correctStakeInput,
            correctDAOOutput,
            correctVoteOutput,
            //correctStake
        ))
    } else {
        false
    }

    sigmaProp(allOf(Coll(
        paideiaCorrectConfig,
        correctConfig,
        validTransaction,
    )))
}