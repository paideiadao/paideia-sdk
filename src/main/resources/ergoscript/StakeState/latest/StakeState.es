{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_STAKING_STATE,
        _IM_PAIDEIA_CONTRACTS_STAKING_STAKE,
        _IM_PAIDEIA_CONTRACTS_STAKING_CHANGESTAKE,
        _IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE,
        _IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT,
        _IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND,
        _IM_PAIDEIA_CONTRACTS_STAKING_PROFITSHARE
    ),configProof)

    val stakingStateContractSignature = configValues(0).get
    val stakeContractSignature = configValues(1).get
    val changeStakeContractSignature = configValues(2).get
    val unstakeContractSignature = configValues(3).get
    val snapshotContractSignature = configValues(4).get
    val compoundContractSignature = configValues(5).get
    val profitShareContractSignature = configValues(6).get

    val stakingStateInput = SELF

    val STAKE = 0.toByte
    val CHANGE_STAKE = 1.toByte
    val UNSTAKE = 2.toByte
    val SNAPSHOT = 3.toByte
    val COMPOUND = 4.toByte
    val PROFIT_SHARE = 5.toByte

    val stakingStateOutput = OUTPUTS(0)

    val transactionType = getVar[Byte](1).get

    val validTransactionType = transactionType >= 0 && transactionType <= 5

    val validOutput = allOf(Coll(
        blake2b256(stakingStateOutput.propositionBytes) == configValues(0).get.slice(1,33),
        stakingStateOutput.tokens(0) == stakingStateInput.tokens(0),
        stakingStateOutput.tokens(1)._1 == stakingStateInput.tokens(1)._1
    ))

    val validStake = {
        if (transactionType == STAKE) {
            blake2b256(INPUTS(1).propositionBytes) == stakeContractSignature.slice(1,33)
        } else {
            true
        }
    }

    val validChangeStake = {
        if (transactionType == CHANGE_STAKE) {
            blake2b256(INPUTS(1).propositionBytes) == changeStakeContractSignature.slice(1,33)
        } else {
            true
        }
    }

    val validUnstake = {
        if (transactionType == UNSTAKE) {
            blake2b256(INPUTS(1).propositionBytes) == unstakeContractSignature.slice(1,33)
        } else {
            true
        }
    }

    val validSnapshot = {
        if (transactionType == SNAPSHOT) {
            blake2b256(INPUTS(1).propositionBytes) == snapshotContractSignature.slice(1,33)
        } else {
        true
        }
    }

    val validCompound = {
        if (transactionType == COMPOUND) {
            blake2b256(INPUTS(1).propositionBytes) == compoundContractSignature.slice(1,33)
        } else {
            true
        }
    }

    val validProfitShare = {
        if (transactionType==PROFIT_SHARE) {
            blake2b256(INPUTS(1).propositionBytes) == profitShareContractSignature.slice(1,33)
        } else {
        true
        }
    }

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        validTransactionType,
        validOutput,
        validStake,
        validChangeStake,
        validUnstake,
        validSnapshot,
        validCompound,
        validProfitShare
    )))
}