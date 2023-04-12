{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID,
        _IM_PAIDEIA_CONTRACTS_STAKING_PROFITSHARE,
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS
    ),configProof)

    val stakingStateTokenId = configValues(0).get
    val profitShareContractSignature = configValues(1).get
    val profitTokenIds = configValues(2).get

    val validProfitShareTx = {
        val stakingStateInput = INPUTS(0)
        val correctStakingState = stakingStateInput.tokens(0)._1 == stakingStateTokenId.slice(6,38)

        val stakeState = stakingStateInput.R4[AvlTree].get

        val whiteListedTokenIds = profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{(i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }
        val profit = stakingStateInput.R5[Coll[Long]].get.slice(3,stakingStateInput.R5[Coll[Long]].get.size).append(whiteListedTokenIds.slice(stakingStateInput.R5[Coll[Long]].get.size-2,whiteListedTokenIds.size).map{(tokId: Coll[Byte]) => 0L})

        val stakingStateOutput = OUTPUTS(0)

        val outputProfit = stakingStateOutput.R5[Coll[Long]].get.slice(3,stakingStateOutput.R5[Coll[Long]].get.size)

        val ergProfit = stakingStateOutput.value - stakingStateInput.value
        val govProfit = stakingStateOutput.tokens(1)._2 - stakingStateInput.tokens(1)._2
        val correctErgProfit = ergProfit >= 0L && outputProfit(1) - profit(1) == ergProfit
        val correctGovProfit = govProfit >= 0L && outputProfit(0) - profit(0) == govProfit
        val correctUpdatedProfit = stakingStateInput.tokens.slice(2,stakingStateInput.tokens.size).zip(stakingStateOutput.tokens.slice(2,stakingStateInput.tokens.size)).forall{
            (io: ((Coll[Byte],Long),(Coll[Byte],Long))) =>
            val i = io._1
            val o = io._2
            val profitIndex = whiteListedTokenIds.indexOf(i._1,-1)
            val tokenProfit = o._2 - i._2
            allOf(Coll(
                i._1 == o._1,
                profitIndex >= 0,
                tokenProfit == outputProfit(profitIndex+2)-profit(profitIndex+2),
                tokenProfit >= 0L
            ))
        }
        val correctNewProfit = stakingStateOutput.tokens.slice(stakingStateInput.tokens.size,stakingStateOutput.tokens.size).forall{
            (o: (Coll[Byte],Long)) =>
            val profitIndex = whiteListedTokenIds.indexOf(o._1,-3)
            val tokenProfit = o._2
            allOf(Coll(
                profitIndex >= 0,
                tokenProfit == outputProfit(profitIndex+2),
                tokenProfit >= 0L
            ))
        }
        allOf(Coll(
            correctErgProfit,
            correctGovProfit,
            correctUpdatedProfit,
            correctNewProfit,
            stakingStateOutput.tokens.size >= stakingStateInput.tokens.size
        ))
    }

    val profitShareOutput = OUTPUTS(1)
    val selfOutput = allOf(Coll(
        blake2b256(profitShareOutput.propositionBytes) == profitShareContractSignature.slice(1,33),
        profitShareOutput.value >= SELF.value
    ))

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        validProfitShareTx,
        selfOutput
    )))
}