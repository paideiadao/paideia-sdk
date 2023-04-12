{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID,
        _IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT,
        _IM_PAIDEIA_STAKING_EMISSION_AMOUNT,
        _IM_PAIDEIA_STAKING_EMISSION_DELAY,
        _IM_PAIDEIA_STAKING_CYCLELENGTH,
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS
    ),configProof)

    val stakingStateTokenId = configValues(0).get
    val snapshotContractSignature = configValues(1).get
    val emissionAmount = byteArrayToLong(configValues(2).get.slice(1,9))
    val emissionDelay = byteArrayToLong(configValues(3).get.slice(1,9)).toInt
    val cycleLength = byteArrayToLong(configValues(4).get.slice(1,9))
    val profitTokenIds = configValues(5).get

    val correctSnapshotTx = {
        val stakingStateInput = INPUTS(0)
        val correctStakingState = stakingStateInput.tokens(0)._1 == stakingStateTokenId.slice(6,38)

        val stakeState = stakingStateInput.R4[AvlTree].get

        val nextSnapshot = stakingStateInput.R5[Coll[Long]].get(0)
        val totalStaked = stakingStateInput.R5[Coll[Long]].get(2)
        val whiteListedTokenIds = profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{(i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }
        val profit = stakingStateInput.R5[Coll[Long]].get.slice(3,stakingStateInput.R5[Coll[Long]].get.size).append(whiteListedTokenIds.slice(stakingStateInput.R5[Coll[Long]].get.size-2,whiteListedTokenIds.size).map{(tokId: Coll[Byte]) => 0L})
        val snapshotsStaked = stakingStateInput.R6[Coll[Long]].get
        val snapshotsTree = stakingStateInput.R7[Coll[AvlTree]].get
        val snapshotsProfit = stakingStateInput.R8[Coll[Coll[Long]]].get

        val emptyDigest = Coll(78,-58,31,72,91,-104,-21,-121,21,63,124,87,-37,79,94,-51,117,85,111,-35,-68,64,59,65,-84,-8,68,31,-34,-114,22,9,0).map{(i: Int) => i.toByte}

        val stakingStateOutput = OUTPUTS(0)

        val outputProfit = stakingStateOutput.R5[Coll[Long]].get.slice(3,stakingStateOutput.R5[Coll[Long]].get.size)
        val newSnapshotsStaked = stakingStateOutput.R6[Coll[Long]].get
        val newSnapshotsTrees = stakingStateOutput.R7[Coll[AvlTree]].get
        val newSnapshotsProfit = stakingStateOutput.R8[Coll[Coll[Long]]].get

        val correctNewSnapshot = allOf(Coll(
            newSnapshotsStaked(newSnapshotsStaked.size-1) == totalStaked,
            newSnapshotsTrees(newSnapshotsTrees.size-1).digest == stakeState.digest,
            newSnapshotsProfit(newSnapshotsProfit.size-1)(0) == min(emissionAmount,stakingStateInput.tokens(1)._2-totalStaked-profit(0)-1)
        ))

        val correctProfitAddedToSnapshot = allOf(Coll(
            newSnapshotsProfit(0).slice(1,profit.size).indices.forall{(i: Int) => newSnapshotsProfit(0)(i+1) == profit(i+1)},
            newSnapshotsProfit(0)(0) == snapshotsProfit(1)(0) + profit(0)
        ))
        
        //When a staker gets rewarded for the staking period his entry gets removed from the snapshot. An empty snapshot is proof of having handled all staker rewards for that period.
        val correctHistoryShift = allOf(Coll( 
                snapshotsTree(0).digest == emptyDigest,
                newSnapshotsTrees.slice(0,emissionDelay-1) == snapshotsTree.slice(1,emissionDelay),
                newSnapshotsStaked.slice(0,emissionDelay-1).indices.forall{(i: Int) => newSnapshotsStaked(i) == snapshotsStaked(i+1)}
            ))

        val profitReset = outputProfit.forall{(p: Long) => p==0L}

        val correctSize = newSnapshotsTrees.size == emissionDelay && 
                        newSnapshotsStaked.size == emissionDelay &&
                        newSnapshotsProfit.size == emissionDelay

        val correctNextShapshot = stakingStateOutput.R5[Coll[Long]].get(0) == nextSnapshot + cycleLength
        val correctTime = nextSnapshot <= CONTEXT.preHeader.timestamp

        allOf(Coll(
            correctStakingState,
            correctNewSnapshot,
            correctHistoryShift,
            correctSize,
            profitReset,
            correctNextShapshot,
            correctTime
        ))
    }

    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val snapshotOutput = OUTPUTS(1)
    val selfOutput = allOf(Coll(
        blake2b256(snapshotOutput.propositionBytes) == snapshotContractSignature.slice(1,33),
        snapshotOutput.value >= SELF.value
    ))

    sigmaProp(allOf(Coll(
        selfOutput,
        correctSnapshotTx,
        correctConfigTokenId
    )))
}