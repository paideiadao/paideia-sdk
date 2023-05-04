{
    val configTokenId = _IM_PAIDEIA_DAO_KEY
    val config = CONTEXT.dataInputs(0)
    val stakeInfoOffset = 8

    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_STAKING_STAKE,
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID,
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS
    ),configProof)

    val stakeContractSignature = configValues(0).get
    val stakingStateTokenId = configValues(1).get
    val profitTokenIds = configValues(2).get

    val correctStakeTx = {

        val stakingStateInput = INPUTS(0)

        val whiteListedTokenIds = profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{(i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }
        val profit = stakingStateInput.R5[Coll[Long]].get.slice(5,stakingStateInput.R5[Coll[Long]].get.size).append(whiteListedTokenIds.slice(stakingStateInput.R5[Coll[Long]].get.size-4,whiteListedTokenIds.size).map{(tokId: Coll[Byte]) => 0L})

        val longIndices = profit.indices.map{(i: Int) => i*8}

        val correctStakingState = stakingStateInput.tokens(0)._1 == configValues(1).get.slice(6,38)

        val stakeState = stakingStateInput.R4[Coll[AvlTree]].get(0)

        val totalStaked = stakingStateInput.R5[Coll[Long]].get(1)
        
        val stakingStateOutput = OUTPUTS(0)
        
        val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get
        val proof   = getVar[Coll[Byte]](2).get

        val userOutput = OUTPUTS(2)
        val mintedKey = userOutput.tokens(0)
    
        val stakeRecord = longIndices.map{(i: Int) => byteArrayToLong(stakeOperations(0)._2.slice(i+stakeInfoOffset,i+8+stakeInfoOffset))}
        val stakeAmount = stakeRecord(0)
        val zeroReward = stakeRecord.slice(1,stakeRecord.size).forall{(l: Long) => l==0L}

        val correctKeyMinted = stakingStateInput.id == mintedKey._1 && stakingStateInput.id == stakeOperations(0)._1 
        val correctAmountMinted = OUTPUTS.flatMap{(b: Box) => b.tokens}.fold(0L, {(z: Long, t: (Coll[Byte], Long)) => z + (if (t._1 == mintedKey._1) t._2 else 0L)}) == 1L

        val tokensStaked = stakeAmount == (stakingStateOutput.tokens(1)._2 - stakingStateInput.tokens(1)._2) && stakeAmount == stakingStateOutput.R5[Coll[Long]].get(1) - totalStaked

        val singleStakeOp = stakeOperations.size == 1

        val correctNewState = stakeState.insert(stakeOperations, proof).get.digest == stakingStateOutput.R4[Coll[AvlTree]].get(0).digest
        
        allOf(Coll(
            correctStakingState,
            correctKeyMinted,
            correctAmountMinted,
            tokensStaked,
            singleStakeOp,
            correctNewState,
            zeroReward
        ))
    }

    val stakeOutput = OUTPUTS(1)
    val selfOutput = allOf(Coll(
        blake2b256(stakeOutput.propositionBytes) == stakeContractSignature.slice(1,33),
        stakeOutput.value >= SELF.value
    ))

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        correctStakeTx,
        selfOutput
    )))
}