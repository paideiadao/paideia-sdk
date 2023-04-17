{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val stakeInfoOffset = 16

    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_STAKING_CHANGESTAKE,
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID,
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS
    ),configProof)

    val changeStakeContractSignature = configValues(0).get
    val stakingStakeTokenId = configValues(1).get
    val profitTokenIds = configValues(2).get

    val validChangeStakeTx = {
        
        val whiteListedTokenIds = profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{(i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }

        val stakingStateInput = INPUTS(0)
        val correctStakingState = stakingStateInput.tokens(0)._1 == stakingStakeTokenId.slice(6,38)
        val stakeState = stakingStateInput.R4[AvlTree].get
        val totalStaked = stakingStateInput.R5[Coll[Long]].get(2)
        val profit = stakingStateInput.R5[Coll[Long]].get.slice(3,stakingStateInput.R5[Coll[Long]].get.size).append(whiteListedTokenIds.slice(stakingStateInput.R5[Coll[Long]].get.size-2,whiteListedTokenIds.size).map{(tokId: Coll[Byte]) => 0L})
        val longIndices = profit.indices.map{(i: Int) => i*8}

        val stakingStateOutput = OUTPUTS(0)
        val outputProfit = stakingStateOutput.R5[Coll[Long]].get.slice(3,stakingStateOutput.R5[Coll[Long]].get.size)

        val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get
        val proof   = getVar[Coll[Byte]](2).get

        val userOutput = OUTPUTS(2)
        val keyInOutput = userOutput.tokens.getOrElse(0,OUTPUTS(0).tokens(0))._1 == stakeOperations(0)._1

        val currentStakeState = stakeState.get(stakeOperations(0)._1, proof).get
        val currentProfits = longIndices.map{(i: Int) => byteArrayToLong(currentStakeState.slice(i+stakeInfoOffset,i+8+stakeInfoOffset))}
        val newProfits = longIndices.map{(i: Int) => byteArrayToLong(stakeOperations(0)._2.slice(i+stakeInfoOffset,i+8+stakeInfoOffset))}
        val combinedProfit = currentProfits.zip(newProfits)

        val currentStakeAmount = currentProfits(0)
        val newStakeAmount = newProfits(0)

        val tokensStaked = newStakeAmount - currentStakeAmount == (stakingStateOutput.tokens(1)._2 - stakingStateInput.tokens(1)._2) && newStakeAmount - currentStakeAmount == stakingStateOutput.R5[Coll[Long]].get(2) - totalStaked

        val singleStakeOp = stakeOperations.size == 1

        val correctNewState = stakeState.update(stakeOperations, proof).get.digest == stakingStateOutput.R4[AvlTree].get.digest

        val noAddedOrNegativeProfit = combinedProfit.slice(1,combinedProfit.size).forall{(p: (Long, Long)) => p._1 >= p._2 && p._2 >= 0L}

        val correctErgProfit = currentProfits(1)-newProfits(1) == stakingStateInput.value - stakingStateOutput.value

        val correctTokenProfit = stakingStateInput.tokens.slice(2,stakingStateInput.tokens.size).forall{
            (token: (Coll[Byte], Long)) =>
            val profitIndex = whiteListedTokenIds.indexOf(token._1,-3)
            val tokenAmountInOutput = stakingStateOutput.tokens.fold(0L, {(z: Long, outputToken: (Coll[Byte], Long)) => if (outputToken._1 == token._1) z + outputToken._2 else z})
            token._2 - tokenAmountInOutput == currentProfits(profitIndex+2) - newProfits(profitIndex+2)
        }
        
        allOf(Coll(
            correctStakingState,
            keyInOutput,
            tokensStaked,
            singleStakeOp,
            correctNewState,
            noAddedOrNegativeProfit,
            correctErgProfit,
            correctTokenProfit
        ))
    }

    val changeStakeOutput = OUTPUTS(1)
    val selfOutput = allOf(Coll(
        blake2b256(changeStakeOutput.propositionBytes) == changeStakeContractSignature.slice(1,33),
        changeStakeOutput.value >= SELF.value
    ))

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        validChangeStakeTx,
        selfOutput
    )))
}