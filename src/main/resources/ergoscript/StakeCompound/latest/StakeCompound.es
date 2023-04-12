{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID,
        _IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND,
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS
    ),configProof)

    val stakingStateTokenId = configValues(0).get
    val compoundContractSignature = configValues(1).get
    val profitTokenIds = configValues(2).get

    val validCompoundTx = {
        val stakingStateInput = INPUTS(0)
        val correctStakingState = stakingStateInput.tokens(0)._1 == stakingStateTokenId.slice(6,38)

        val stakeState = stakingStateInput.R4[AvlTree].get

        val totalStaked = stakingStateInput.R5[Coll[Long]].get(2)
        val whiteListedTokenIds = profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{(i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }
        val profit = stakingStateInput.R5[Coll[Long]].get.slice(3,stakingStateInput.R5[Coll[Long]].get.size).append(whiteListedTokenIds.slice(stakingStateInput.R5[Coll[Long]].get.size-2,whiteListedTokenIds.size).map{(tokId: Coll[Byte]) => 0L})
        val snapshotsStaked = stakingStateInput.R6[Coll[Long]].get
        val snapshotsTree = stakingStateInput.R7[Coll[AvlTree]].get
        val snapshotsProfit = stakingStateInput.R8[Coll[Coll[Long]]].get
        val longIndices = profit.indices.map{(i: Int) => i*8}

        val notFound = profit.map{(l: Long) => -1L}
        
        val stakingStateOutput = OUTPUTS(0)
        val compoundOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get
        val proof = getVar[Coll[Byte]](2).get
        val snapshotProof = getVar[Coll[Byte]](3).get
        val removeProof = getVar[Coll[Byte]](4).get

        val keys = compoundOperations.map{(kv: (Coll[Byte], Coll[Byte])) => kv._1}

        val filteredCompoundOperations = compoundOperations.filter{(kv: (Coll[Byte], Coll[Byte])) => byteArrayToLong(kv._2.slice(0,8)) > 0}

        val currentStakes: Coll[Coll[Long]] = stakeState.getMany(keys,proof).map{
            (b: Option[Coll[Byte]]) =>
            if (b.isDefined) {
            longIndices.map{(i: Int) => byteArrayToLong(b.get.slice(i,i+8))}
            } else {
            notFound
            }
        }

        val snapshotStakes = snapshotsTree(0).getMany(keys,snapshotProof).map{(b: Option[Coll[Byte]]) => longIndices.map{(i: Int) => byteArrayToLong(b.get.slice(i,i+8))}}
        val newStakes: Coll[Coll[Long]] = compoundOperations.map{(kv: (Coll[Byte], Coll[Byte])) => longIndices.map{(i: Int) => byteArrayToLong(kv._2.slice(i,i+8))}}
        val snapshotStaked = snapshotsStaked(0)

        val snapshotProfit = snapshotsProfit(0)

        val keyIndices = keys.indices

        val rewards = keyIndices.map{
            (index: Int) => {
            if (currentStakes(index)(0)>=0L) {
                val r = snapshotProfit.map{(p: Long) => (snapshotStakes(index)(0).toBigInt * p.toBigInt / snapshotStaked)}
                val newStake: Coll[BigInt] = currentStakes(index).zip(r).map{(ll: (Long,BigInt)) => ll._1+ll._2}
                (r,newStake == newStakes(index).map{(s: Long) => s.toBigInt})
            } else {
                (snapshotProfit.map{(l: Long) => 0.toBigInt},true)
            }
            }
        }

        val validCompounds = allOf(rewards.map{
            (reward: (Coll[BigInt],Boolean)) =>
            reward._2
        })

        val totalRewards = rewards.fold(0.toBigInt, {(z: BigInt, reward: (Coll[BigInt],Boolean)) => z + reward._1(0)})

        val correctTotalStaked = totalStaked.toBigInt + totalRewards == stakingStateOutput.R5[Coll[Long]].get(2).toBigInt

        val correctSnapshot = snapshotsTree(0).remove(keys, removeProof).get.digest == stakingStateOutput.R7[Coll[AvlTree]].get(0).digest
        
        val correctNewState = stakeState.update(filteredCompoundOperations, proof).get.digest == stakingStateOutput.R4[AvlTree].get.digest
        
        allOf(Coll(
            correctStakingState,
            validCompounds,
            correctTotalStaked,
            correctSnapshot,
            correctNewState
        ))
    }

    val compoundOutput = OUTPUTS(1)
    val selfOutput = allOf(Coll(
        blake2b256(compoundOutput.propositionBytes) == compoundContractSignature.slice(1,33),
        compoundOutput.value >= SELF.value
    ))

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        validCompoundTx,
        selfOutput
    )))
}