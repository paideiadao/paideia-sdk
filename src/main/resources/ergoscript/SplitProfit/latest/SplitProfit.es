{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_TREASURY,
        _IM_PAIDEIA_CONTRACTS_STAKING_STATE,
        _IM_PAIDEIA_PROFIT_SHARING_PCT,
        _IM_PAIDEIA_DAO_GOVERNANCE_TOKENID,
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS
    ),configProof)

    val governanceTokenId = configValues(3).get.slice(6,38)

    val profitTokenIds = configValues(4).get.slice(0,(configValues(4).get.size-6)/37).indices.map{(i: Int) =>
        configValues(4).get.slice(6+(37*i)+5,6+(37*(i+1)))
    }

    val profitSharingPct = configValues(2).get(1)

    val treasuryOutput = OUTPUTS.filter{(b: Box) => blake2b256(b.propositionBytes) == configValues(0).get.slice(1,33)}(0)
    val minerOutput = OUTPUTS.filter{(b: Box) => blake2b256(b.propositionBytes) != configValues(0).get.slice(1,33) && blake2b256(b.propositionBytes) != configValues(1).get.slice(1,33)}(0)

    val tokensIn = INPUTS.flatMap{(b: Box) => b.tokens}.fold(0L, {(x: Long, t: (Coll[Byte], Long)) => x + t._2})

    val tokensOut = OUTPUTS.flatMap{(b: Box) => b.tokens}.fold(0L, {(x: Long, t: (Coll[Byte], Long)) => x + t._2})
    
    val validTx = if (profitSharingPct <= 0) {

        allOf(Coll(
            OUTPUTS.size == 2,
            minerOutput.value <= 5000000L,
            minerOutput.tokens.size == 0,
            tokensIn == tokensOut,
            treasuryOutput.value >= 1000000L
        ))

    } else {

        val stakingOutput = OUTPUTS(0)
        val stakingInput = INPUTS(0)

        val tokenSplits = Coll(governanceTokenId).append(profitTokenIds).forall{(tokenId: Coll[Byte]) => {
            val stakingInputTokens = stakingInput.tokens.fold(0L, {(z: Long, t: (Coll[Byte], Long)) => z + (if (t._1 == tokenId) t._2 else 0L)})
            val stakingOutputTokens = stakingOutput.tokens.fold(0L, {(z: Long, t: (Coll[Byte], Long)) => z + (if (t._1 == tokenId) t._2 else 0L)})
            val treasuryTokens = treasuryOutput.tokens.fold(0L, {(z: Long, t: (Coll[Byte], Long)) => z + (if (t._1 == tokenId) t._2 else 0L)})

            (stakingOutputTokens - stakingInputTokens + treasuryTokens)*profitSharingPct/100 == (stakingOutputTokens - stakingInputTokens)
        }}

        allOf(Coll(
            OUTPUTS.size == 4,
            blake2b256(stakingOutput.propositionBytes) == configValues(1).get.slice(1,33),
            minerOutput.value <= 5000000L,
            minerOutput.tokens.size == 0,
            tokensIn == tokensOut,
            treasuryOutput.value >= 1000000L,
            (stakingOutput.value - stakingInput.value + (treasuryOutput.value-1000000L))*profitSharingPct/100 == (stakingOutput.value - stakingInput.value),
            tokenSplits
        ))

    }

    sigmaProp(correctConfigTokenId && validTx)
}