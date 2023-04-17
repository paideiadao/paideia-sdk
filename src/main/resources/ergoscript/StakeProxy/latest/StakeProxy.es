{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val correctConfigTokenId = config.tokens(0)._1 == configTokenId
    val stakeInfoOffset = 16

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_STAKING_STATE_TOKENID,
        _IM_PAIDEIA_DAO_NAME
    ),configProof)

    val plasmaStakingInput = INPUTS(0)
    val correctPlasmaStakingInput = INPUTS(0).tokens(0)._1 == configValues(0).get.slice(6,38)
    val plasmaStakingOutput = OUTPUTS(0)
    val userOutput = OUTPUTS(2)

    val stakeState = plasmaStakingInput.R4[AvlTree].get
    val profit = plasmaStakingInput.R5[Coll[Long]].get.slice(3,plasmaStakingInput.R5[Coll[Long]].get.size)
    val totalStaked = plasmaStakingInput.R5[Coll[Long]].get(2)
    val longIndices = profit.indices.map{(i: Int) => i*8}

    val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get
    val proof   = getVar[Coll[Byte]](2).get

    val mintedKey = userOutput.tokens(0)

    val stakeRecord = longIndices.map{(i: Int) => byteArrayToLong(stakeOperations(0)._2.slice(i+stakeInfoOffset,i+8+stakeInfoOffset))}
    val stakeAmount = stakeRecord(0)
    val zeroReward = stakeRecord.slice(1,stakeRecord.size).forall{(l: Long) => l==0L}

    val daoName = configValues(1).get.slice(5,configValues(1).get.size)

    val correctKeyMinted = allOf(Coll(
        plasmaStakingInput.id == mintedKey._1,
        plasmaStakingInput.id == stakeOperations(0)._1,
        userOutput.R4[Coll[Byte]].get == daoName++_STAKE_KEY,
        userOutput.R5[Coll[Byte]].get == _POWERED_BY_PAIDEIA,
        userOutput.R6[Coll[Byte]].get == Coll(48.toByte)
    ))

    val correctAmountMinted = OUTPUTS.flatMap{(b: Box) => b.tokens}.fold(0L, {(z: Long, token: (Coll[Byte], Long)) => if (token._1==plasmaStakingInput.id) z + token._2 else z}) == 1L

    val tokensStaked = stakeAmount == (plasmaStakingOutput.tokens(1)._2 - plasmaStakingInput.tokens(1)._2) && stakeAmount == plasmaStakingOutput.R5[Coll[Long]].get(2) - totalStaked

    val singleStakeOp = stakeOperations.size == 1

    val correctNewState = stakeState.insert(stakeOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
    
    val validStakeTx = allOf(Coll(
        userOutput.propositionBytes == SELF.R4[Coll[Byte]].get,
        correctPlasmaStakingInput,
        correctKeyMinted,
        correctAmountMinted,
        tokensStaked,
        singleStakeOp,
        correctNewState,
        zeroReward
    ))

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        validStakeTx
    )))
}