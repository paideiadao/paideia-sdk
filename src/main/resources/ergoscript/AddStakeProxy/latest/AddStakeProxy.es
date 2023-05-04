{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val stakeInfoOffset = 8

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_STAKING_STATE_TOKENID
    ),configProof)

    val plasmaStakingInput = INPUTS(0)
    val correctPlasmaStakingInput = INPUTS(0).tokens(0)._1 == configValues(0).get.slice(6,38)
    val plasmaStakingOutput = OUTPUTS(0)
    val userOutput = OUTPUTS(2)

    val stakeState = plasmaStakingInput.R4[Coll[AvlTree]].get(0)

    val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get
    val proof   = getVar[Coll[Byte]](2).get

    val keyInOutput = userOutput.tokens(0)._1 == stakeOperations(0)._1

    val newStakeAmount = byteArrayToLong(stakeOperations(0)._2.slice(stakeInfoOffset,stakeInfoOffset+8))

    val currentStakeState = stakeState.get(stakeOperations(0)._1, proof).get

    val currentStakeAmount = byteArrayToLong(currentStakeState.slice(stakeInfoOffset,stakeInfoOffset+8))

    val tokensStaked = newStakeAmount - currentStakeAmount == SELF.tokens(1)._2

    val singleStakeOp = stakeOperations.size == 1

    val correctNewState = stakeState.update(stakeOperations, proof).get.digest == plasmaStakingOutput.R4[Coll[AvlTree]].get(0).digest
    
    val validTx = allOf(Coll(
        correctPlasmaStakingInput,
        userOutput.propositionBytes == SELF.R4[Coll[Byte]].get,
        keyInOutput,
        tokensStaked,
        singleStakeOp,
        correctNewState
    ))

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        validTx
    )))
}