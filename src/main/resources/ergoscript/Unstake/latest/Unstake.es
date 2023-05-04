{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)

    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val stakeInfoOffset = 8

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID,
        _IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE,
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS
    ),configProof)

    val stakingStateTokenId = configValues(0).get
    val unstakeContractSignature = configValues(1).get
    val profitTokenIds = configValues(2).get

    val validUnstakeTx = {
        
        val stakingStateInput = INPUTS(0)

        val correctStakingState = stakingStateInput.tokens(0)._1 == stakingStateTokenId.slice(6,38)

        val stakeState = stakingStateInput.R4[Coll[AvlTree]].get(0)
        val totalStaked = stakingStateInput.R5[Coll[Long]].get(1)
        val whiteListedTokenIds = profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{(i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }
        val profit = stakingStateInput.R5[Coll[Long]].get.slice(5,stakingStateInput.R5[Coll[Long]].get.size).append(whiteListedTokenIds.slice(stakingStateInput.R5[Coll[Long]].get.size-4,whiteListedTokenIds.size).map{(tokId: Coll[Byte]) => 0L})
        val longIndices = profit.indices.map{(i: Int) => i*8}

        val stakingStateOutput = OUTPUTS(0)

        val keys  = getVar[Coll[(Coll[Byte],Coll[Byte])]](1).get.map{(kv: (Coll[Byte], Coll[Byte])) => kv._1}
        val proof   = getVar[Coll[Byte]](2).get
        val removeProof = getVar[Coll[Byte]](3).get

        val userInput = INPUTS(2)

        val keyInInput = userInput.tokens(0)._1 == keys(0)

        val currentStakeState = stakeState.get(keys(0), proof).get
        val currentProfits = longIndices.map{(i: Int) => byteArrayToLong(currentStakeState.slice(i+stakeInfoOffset,i+8+stakeInfoOffset))}

        val currentStakeAmount = currentProfits(0)

        val tokensUnstaked = currentStakeAmount == (stakingStateInput.tokens(1)._2 - stakingStateOutput.tokens(1)._2) && currentStakeAmount == totalStaked - stakingStateOutput.R5[Coll[Long]].get(1)
        val correctErgProfit = currentProfits(1) == stakingStateInput.value - stakingStateOutput.value

        val correctTokenProfit = stakingStateInput.tokens.slice(2,stakingStateInput.tokens.size).forall{
                (token: (Coll[Byte], Long)) =>
                val profitIndex = whiteListedTokenIds.indexOf(token._1,-3)
                val tokenAmountInOutput = stakingStateOutput.tokens.fold(0L, {(z: Long, outputToken: (Coll[Byte], Long)) => if (outputToken._1 == token._1) z + outputToken._2 else z})
                token._2 - tokenAmountInOutput == currentProfits(profitIndex+2)
            }

        val singleStakeOp = keys.size == 1

        val correctNewState = stakeState.remove(keys, removeProof).get.digest == stakingStateOutput.R4[Coll[AvlTree]].get(0).digest
        
        allOf(Coll(
            correctStakingState,
            keyInInput,
            tokensUnstaked,
            correctErgProfit,
            correctTokenProfit,
            singleStakeOp,
            correctNewState
        ))
    }

    val unstakeOutput = OUTPUTS(1)
    val selfOutput = allOf(Coll(
        blake2b256(unstakeOutput.propositionBytes) == unstakeContractSignature.slice(1,33),
        unstakeOutput.value >= SELF.value
    ))

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        validUnstakeTx,
        selfOutput
    )))
}