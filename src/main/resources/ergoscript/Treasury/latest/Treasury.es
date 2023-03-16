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

    val validStakeOp = if (!validAction) {
        val stakeStateInput = INPUTS(0)
        val stakeStateOutput = OUTPUTS(0)

        val paideiaConfig = CONTEXT.dataInputs(1)

        val correctPaideiaConfig = paideiaConfig.tokens(0)._1 == _IM_PAIDEIA_DAO_KEY

        val paideiaTokenId = _IM_PAIDEIA_TOKEN_ID

        val paideiaProof = getVar[Coll[Byte]](0).get

        val treasuryOutput = OUTPUTS.filter{(b: Box) => b.propositionBytes == SELF.propositionBytes}(0)
        val treasuryInInput = INPUTS.filter{(b: Box) => b.propositionBytes == SELF.propositionBytes}

        val treasuryNerg = treasuryInInput.fold(0L, {(z: Long, b: Box) => z + b.value})

        def tokensInBoxes(tokenId: Coll[Byte]): Long = 
            treasuryInInput.flatMap{(b: Box) => b.tokens}
                .fold(0L, {(z: Long, token: (Coll[Byte], Long)) => z + (if (token._1 == tokenId) token._2 else 0L)})

        val treasuryPaideia = tokensInBoxes(paideiaTokenId)

        val noMissingTokens = treasuryInInput.flatMap{
            (b: Box) => b.tokens
        }.forall{(t: (Coll[Byte], Long)) => t._1 == paideiaTokenId || treasuryOutput.tokens.exists{(to: (Coll[Byte], Long)) => to._1 == t._1}}

        val correctTokens = treasuryOutput.tokens.filter{(t: (Coll[Byte], Long)) => t._1 != paideiaTokenId}
                .forall{(t: (Coll[Byte], Long)) =>
                    t._2 == tokensInBoxes(t._1)
                }

        if (stakeStateOutput.R5[Coll[Long]].get(0) > stakeStateInput.R5[Coll[Long]].get(0)) {
            val paideiaConfigValues = paideiaConfig.R4[AvlTree].get.getMany(Coll(
                _IM_PAIDEIA_FEE_EMIT_PAIDEIA,
                _IM_PAIDEIA_FEE_EMIT_OPERATOR_PAIDEIA,
                _IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT,
                _IM_PAIDEIA_FEE_OPERATOR_MAX_ERG
            ), paideiaProof)

            val baseFee = byteArrayToLong(paideiaConfigValues(0).get.slice(1,9))
            val paideiaFee = baseFee*stakeStateOutput.R5[Coll[Long]].get(1)+1L

            val correctErg = treasuryOutput.value >= treasuryNerg - byteArrayToLong(paideiaConfigValues(3).get.slice(1,9))
            val correctPaideia = treasuryOutput.tokens.fold(0L, {(z: Long, t: (Coll[Byte], Long)) => z + (if (t._1 == paideiaTokenId) t._2 else 0L)}) >=
                treasuryPaideia - 
                paideiaFee -
                byteArrayToLong(paideiaConfigValues(1).get.slice(1,9))

            val splitProfitOutput = OUTPUTS.filter{(b: Box) => blake2b256(b.propositionBytes) == paideiaConfigValues(2).get.slice(1,33)}(0)

            allOf(Coll(
                correctErg,
                correctPaideia,
                correctTokens,
                noMissingTokens,
                splitProfitOutput.tokens(0)._1 == paideiaTokenId,
                splitProfitOutput.tokens(0)._2 >= paideiaFee
            ))
        } else {
            if (stakeStateOutput.R5[Coll[Long]].get(2) > stakeStateInput.R5[Coll[Long]].get(2) && stakeStateOutput.tokens(1)._2 == stakeStateInput.tokens(1)._2) {
                val paideiaConfigValues = paideiaConfig.R4[AvlTree].get.getMany(Coll(
                    _IM_PAIDEIA_FEE_COMPOUND_OPERATOR_PAIDEIA,
                    _IM_PAIDEIA_FEE_OPERATOR_MAX_ERG
                ), paideiaProof)

                val correctErg = treasuryOutput.value >= treasuryNerg - byteArrayToLong(paideiaConfigValues(1).get.slice(1,9))
                val correctPaideia = treasuryOutput.tokens.fold(0L, {(z: Long, t: (Coll[Byte], Long)) => z + (if (t._1 == paideiaTokenId) t._2 else 0L)}) >=
                    treasuryPaideia - 
                    byteArrayToLong(paideiaConfigValues(0).get.slice(1,9))

                allOf(Coll(
                    correctErg,
                    correctPaideia,
                    correctTokens,
                    noMissingTokens
                ))
            } else {
                false
            }
        }
    } else {
        false
    }

    sigmaProp(
        anyOf(
            Coll(
                validAction,
                validStakeOp
            )
        )
    )
}