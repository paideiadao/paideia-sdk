{
    // val configTokenId = _IM_PAIDEIA_DAO_KEY 
    // val config = CONTEXT.dataInputs(0)
    // val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    // val configProof = getVar[Coll[Byte]](0).get

    // val configValues = config.R4[AvlTree].get.getMany(Coll(
    //     _IM_PAIDEIA_STAKING_STATE_TOKENID
    // ),configProof)

    // val plasmaStakingInput = INPUTS(0)
    // val plasmaStakingOutput = OUTPUTS(0)
    // val userOutput = OUTPUTS(1)

    // val stakeState = SELF.R4[AvlTree].get
    // val profit = plasmaStakingInput.R5[Coll[Long]].get.slice(3,plasmaStakingInput.R5[Coll[Long]].get.size)
    // val totalStaked = SELF.R5[Coll[Long]].get(2)
    // val longIndices = profit.indices.map{(i: Int) => i*8}

    // val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get
    // val proof   = getVar[Coll[Byte]](2).get

    // val mintedKey = userOutput.tokens(0)

    // val stakeRecord = longIndices.map{(i: Int) => byteArrayToLong(stakeOperations(0)._2.slice(i,i+8))}
    // val stakeAmount = stakeRecord(0)
    // val zeroReward = stakeRecord.slice(1,stakeRecord.size).forall{(l: Long) => l==0L}

    // val correctKeyMinted = plasmaStakingInput.id == mintedKey._1 && plasmaStakingInput.id == stakeOperations(0)._1 
    // val correctAmountMinted = mintedKey._2 == 1

    // val tokensStaked = stakeAmount == (plasmaStakingOutput.tokens(1)._2 - plasmaStakingInput.tokens(1)._2) && stakeAmount == plasmaStakingOutput.R5[Coll[Long]].get(2) - totalStaked

    // val singleStakeOp = stakeOperations.size == 1

    // val correctNewState = stakeState.insert(stakeOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
    
    // allOf(Coll(
    //     correctKeyMinted,
    //     correctAmountMinted,
    //     tokensStaked,
    //     singleStakeOp,
    //     correctNewState,
    //     zeroReward
    // ))

    sigmaProp(true && CONTEXT.preHeader.timestamp > 2)
}