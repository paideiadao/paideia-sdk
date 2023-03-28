{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_STAKING_STATE_TOKENID,
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS
    ),configProof)

    val plasmaStakingInput = INPUTS(0)
    val correctPlasmaStakingInput = INPUTS(0).tokens(0)._1 == configValues(0).get.slice(6,38)
    val plasmaStakingOutput = OUTPUTS(0)
    val userOutput = OUTPUTS(1)

    val stakeState = plasmaStakingInput.R4[AvlTree].get

    val newStakeRecord = SELF.R5[Coll[Byte]].get
    val newStake = byteArrayToLong(newStakeRecord.slice(0,8))
    val longIndices = newStakeRecord.slice(0,newStakeRecord.size/8).indices

    val whiteListedTokenIds = configValues(1).get.slice(0,(configValues(1).get.size-6)/37).indices.map{(i: Int) =>
        configValues(1).get.slice(6+(37*i)+5,6+(37*(i+1)))
    }

    val stakeOperations = Coll((SELF.tokens(0)._1,newStakeRecord))
    val proof = getVar[Coll[Byte]](1).get

    val correctReturnedProfits = {
        val currentStakeState = stakeState.get(stakeOperations(0)._1, proof).get
        val currentProfits = longIndices.map{(i: Int) => byteArrayToLong(currentStakeState.slice(i*8,i*8+8))}
        val newProfits = longIndices.map{(i: Int) => byteArrayToLong(stakeOperations(0)._2.slice(i*8,i*8+8))}
        val combinedProfit = currentProfits.zip(newProfits)

        val currentStakeAmount = currentProfits(0)
        val newStakeAmount = newProfits(0)

        val tokensUnstaked = currentStakeAmount - newStakeAmount == userOutput.tokens.fold(0L, {(z: Long, token: (Coll[Byte], Long)) => if (token._1 == plasmaStakingInput.tokens(1)._1) z + token._2 else z})

        val correctErgProfit = currentProfits(1)-newProfits(1) == userOutput.value-1000000L

        val correctTokenProfit = plasmaStakingInput.tokens.slice(2,plasmaStakingInput.tokens.size).forall{
            (token: (Coll[Byte], Long)) =>
            val profitIndex = whiteListedTokenIds.indexOf(token._1,-3)
            val tokenAmountInOutput = userOutput.tokens.fold(0L, {(z: Long, outputToken: (Coll[Byte], Long)) => if (outputToken._1 == token._1) z + outputToken._2 else z})
            tokenAmountInOutput == currentProfits(profitIndex+2) - newProfits(profitIndex+2)
        }
        
        allOf(Coll(
            tokensUnstaked,
            correctErgProfit,
            correctTokenProfit
        ))
    }

    val validTx = if (newStake > 0) {

        val keyInOutput = userOutput.tokens(0)._1 == stakeOperations(0)._1

        val correctNewState = stakeState.update(stakeOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
   
        allOf(Coll(
            keyInOutput,
            correctNewState
        ))
    } else {
        val keys = Coll(SELF.tokens(0)._1)
        val removeProof = getVar[Coll[Byte]](2).get

        val keyInInput = SELF.tokens(0)._1 == keys(0)

        val correctNewState = stakeState.remove(keys, removeProof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
        
        allOf(Coll(
            keyInInput,
            correctNewState
        ))
    }

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        correctReturnedProfits,
        validTx
    )))
}