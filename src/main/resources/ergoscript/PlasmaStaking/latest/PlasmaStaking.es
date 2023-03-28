{
    val configTokenId = _IM_PAIDEIA_DAO_KEY 
    val config = CONTEXT.dataInputs(0)
    val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = config.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_STAKING_EMISSION_AMOUNT,
        _IM_PAIDEIA_STAKING_EMISSION_DELAY,
        _IM_PAIDEIA_STAKING_CYCLELENGTH,
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS,
        _IM_PAIDEIA_STAKING_PROFIT_THRESHOLDS,
        _IM_PAIDEIA_CONTRACTS_STAKING
    ),configProof)

    val stakeState = SELF.R4[AvlTree].get
    val emissionAmount = byteArrayToLong(configValues(0).get.slice(1,9))
    val emissionDelay = byteArrayToLong(configValues(1).get.slice(1,9)).toInt
    val cycleLength = byteArrayToLong(configValues(2).get.slice(1,9))
    val nextSnapshot = SELF.R5[Coll[Long]].get(0)
    val stakers = SELF.R5[Coll[Long]].get(1)
    val totalStaked = SELF.R5[Coll[Long]].get(2)
    val whiteListedTokenIds = configValues(3).get.slice(0,(configValues(3).get.size-6)/37).indices.map{(i: Int) =>
        configValues(3).get.slice(6+(37*i)+5,6+(37*(i+1)))
    }
    val profit = SELF.R5[Coll[Long]].get.slice(3,SELF.R5[Coll[Long]].get.size).append(whiteListedTokenIds.slice(SELF.R5[Coll[Long]].get.size-2,whiteListedTokenIds.size).map{(tokId: Coll[Byte]) => 0L})
    val snapshotsStaked = SELF.R6[Coll[Long]].get
    val snapshotsTree = SELF.R7[Coll[AvlTree]].get
    val snapshotsProfit = SELF.R8[Coll[Coll[Long]]].get
    val longIndices = profit.indices.map{(i: Int) => i*8}

    val STAKE = 0.toByte
    val CHANGE_STAKE = 1.toByte
    val UNSTAKE = 2.toByte
    val SNAPSHOT = 3.toByte
    val COMPOUND = 4.toByte
    val PROFIT_SHARE = 5.toByte

    val emptyDigest = Coll(78,-58,31,72,91,-104,-21,-121,21,63,124,87,-37,79,94,-51,117,85,111,-35,-68,64,59,65,-84,-8,68,31,-34,-114,22,9,0).map{(i: Int) => i.toByte}
    
    val notFound = profit.map{(l: Long) => -1L}
    
    val plasmaStakingOutput = OUTPUTS(0)

    val outputProfit = plasmaStakingOutput.R5[Coll[Long]].get.slice(3,plasmaStakingOutput.R5[Coll[Long]].get.size)

    val transactionType = getVar[Byte](1).get

    val validTransactionType = transactionType >= 0 && transactionType <= 5

    val validOutput = allOf(Coll(
        blake2b256(plasmaStakingOutput.propositionBytes) == configValues(5).get.slice(1,33),
        plasmaStakingOutput.tokens(0) == SELF.tokens(0),
        plasmaStakingOutput.tokens(1)._1 == SELF.tokens(1)._1
    ))

    val validStake = {
        if (transactionType == STAKE) {
        val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](2).get
        val proof   = getVar[Coll[Byte]](3).get

        val userOutput = OUTPUTS(1)
        val mintedKey = userOutput.tokens(0)

        val stakeRecord = longIndices.map{(i: Int) => byteArrayToLong(stakeOperations(0)._2.slice(i,i+8))}
        val stakeAmount = stakeRecord(0)
        val zeroReward = stakeRecord.slice(1,stakeRecord.size).forall{(l: Long) => l==0L}

        val correctKeyMinted = SELF.id == mintedKey._1 && SELF.id == stakeOperations(0)._1 
        val correctAmountMinted = mintedKey._2 == 1

        val tokensStaked = stakeAmount == (plasmaStakingOutput.tokens(1)._2 - SELF.tokens(1)._2) && stakeAmount == plasmaStakingOutput.R5[Coll[Long]].get(2) - totalStaked

        val singleStakeOp = stakeOperations.size == 1

        val correctNewState = stakeState.insert(stakeOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
        
        allOf(Coll(
            correctKeyMinted,
            correctAmountMinted,
            tokensStaked,
            singleStakeOp,
            correctNewState,
            zeroReward
        ))
        } else {
        true
        }
    }

    val validChangeStake = {
        if (transactionType == CHANGE_STAKE) {
            val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](2).get
            val proof   = getVar[Coll[Byte]](3).get

            val userOutput = OUTPUTS(1)

            val keyInOutput = userOutput.tokens.getOrElse(0,OUTPUTS(0).tokens(0))._1 == stakeOperations(0)._1

            val currentStakeState = stakeState.get(stakeOperations(0)._1, proof).get
            val currentProfits = longIndices.map{(i: Int) => byteArrayToLong(currentStakeState.slice(i,i+8))}
            val newProfits = longIndices.map{(i: Int) => byteArrayToLong(stakeOperations(0)._2.slice(i,i+8))}
            val combinedProfit = currentProfits.zip(newProfits)

            val currentStakeAmount = currentProfits(0)
            val newStakeAmount = newProfits(0)

            val tokensStaked = newStakeAmount - currentStakeAmount == (plasmaStakingOutput.tokens(1)._2 - SELF.tokens(1)._2) && newStakeAmount - currentStakeAmount == plasmaStakingOutput.R5[Coll[Long]].get(2) - totalStaked

            val singleStakeOp = stakeOperations.size == 1

            val correctNewState = stakeState.update(stakeOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest

            val noAddedOrNegativeProfit = combinedProfit.slice(1,combinedProfit.size).forall{(p: (Long, Long)) => p._1 >= p._2 && p._2 >= 0L}

            val correctErgProfit = currentProfits(1)-newProfits(1) == SELF.value - plasmaStakingOutput.value

            val correctTokenProfit = SELF.tokens.slice(2,SELF.tokens.size).forall{
                (token: (Coll[Byte], Long)) =>
                val profitIndex = whiteListedTokenIds.indexOf(token._1,-3)
                val tokenAmountInOutput = plasmaStakingOutput.tokens.fold(0L, {(z: Long, outputToken: (Coll[Byte], Long)) => if (outputToken._1 == token._1) z + outputToken._2 else z})
                token._2 - tokenAmountInOutput == currentProfits(profitIndex+2) - newProfits(profitIndex+2)
            }
            
            allOf(Coll(
                keyInOutput,
                tokensStaked,
                singleStakeOp,
                correctNewState,
                noAddedOrNegativeProfit,
                correctErgProfit,
                correctTokenProfit
            ))
        } else {
            true
        }
    }

    val validUnstake = {
        if (transactionType == UNSTAKE) {
        val keys  = getVar[Coll[(Coll[Byte],Coll[Byte])]](2).get.map{(kv: (Coll[Byte], Coll[Byte])) => kv._1}
        val proof   = getVar[Coll[Byte]](3).get
        val removeProof = getVar[Coll[Byte]](4).get

        val userInput = INPUTS(1)

        val keyInInput = userInput.tokens(0)._1 == keys(0)

        val currentStakeState = stakeState.get(keys(0), proof).get
        val currentProfits = longIndices.map{(i: Int) => byteArrayToLong(currentStakeState.slice(i,i+8))}

        val currentStakeAmount = currentProfits(0)

        val tokensUnstaked = currentStakeAmount == (SELF.tokens(1)._2 - plasmaStakingOutput.tokens(1)._2) && currentStakeAmount == totalStaked - plasmaStakingOutput.R5[Coll[Long]].get(2)
        val correctErgProfit = currentProfits(1) == SELF.value - plasmaStakingOutput.value

        val correctTokenProfit = SELF.tokens.slice(2,SELF.tokens.size).forall{
                (token: (Coll[Byte], Long)) =>
                val profitIndex = whiteListedTokenIds.indexOf(token._1,-3)
                val tokenAmountInOutput = plasmaStakingOutput.tokens.fold(0L, {(z: Long, outputToken: (Coll[Byte], Long)) => if (outputToken._1 == token._1) z + outputToken._2 else z})
                token._2 - tokenAmountInOutput == currentProfits(profitIndex+2)
            }

        val singleStakeOp = keys.size == 1

        val correctNewState = stakeState.remove(keys, removeProof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
        
        allOf(Coll(
            keyInInput,
            tokensUnstaked,
            correctErgProfit,
            correctTokenProfit,
            singleStakeOp,
            correctNewState
        ))
        } else {
        true
        }
    }

    val validSnapshot = {
        if (transactionType == SNAPSHOT) {
        val correctSnapshotUpdate = {
            val newSnapshotsStaked = plasmaStakingOutput.R6[Coll[Long]].get
            val newSnapshotsTrees = plasmaStakingOutput.R7[Coll[AvlTree]].get
            val newSnapshotsProfit = plasmaStakingOutput.R8[Coll[Coll[Long]]].get

            val correctNewSnapshot = allOf(Coll(
                newSnapshotsStaked(newSnapshotsStaked.size-1) == totalStaked,
                newSnapshotsTrees(newSnapshotsTrees.size-1).digest == stakeState.digest,
                newSnapshotsProfit(newSnapshotsProfit.size-1).slice(1,profit.size).indices.forall{(i: Int) => newSnapshotsProfit(newSnapshotsProfit.size-1)(i+1) == profit(i+1)},
                newSnapshotsProfit(newSnapshotsProfit.size-1)(0) == profit(0) + min(emissionAmount,SELF.tokens(1)._2-totalStaked-profit(0))
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

            val correctNextShapshot = plasmaStakingOutput.R5[Coll[Long]].get(0) == nextSnapshot + cycleLength
            val correctTime = nextSnapshot <= CONTEXT.preHeader.timestamp

            allOf(Coll(
                correctNewSnapshot,
                correctHistoryShift,
                correctSize,
                profitReset,
                correctNextShapshot,
                correctTime
            ))
        }

        allOf(Coll(
            correctSnapshotUpdate
        ))
        } else {
        true
        }
    }

    val validCompound = {
        if (transactionType == COMPOUND) {
        val compoundOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](2).get
        val proof = getVar[Coll[Byte]](3).get
        val snapshotProof = getVar[Coll[Byte]](4).get
        val removeProof = getVar[Coll[Byte]](5).get

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
                val r = snapshotProfit.map{(p: Long) => (snapshotStakes(index)(0) * p / snapshotStaked)}
                val newStake: Coll[Long] = currentStakes(index).zip(r).map{(ll: (Long,Long)) => ll._1+ll._2}
                (r,newStake == newStakes(index))
            } else {
                (snapshotProfit.map{(l: Long) => 0L},true)
            }
            }
        }

        val validCompounds = allOf(rewards.map{
            (reward: (Coll[Long],Boolean)) =>
            reward._2
        })

        val totalRewards = rewards.fold(0L, {(z: Long, reward: (Coll[Long],Boolean)) => z + reward._1(0)})

        val correctTotalStaked = totalStaked + totalRewards == plasmaStakingOutput.R5[Coll[Long]].get(2)

        val correctSnapshot = snapshotsTree(0).remove(keys, removeProof).get.digest == plasmaStakingOutput.R7[Coll[AvlTree]].get(0).digest
        
        val correctNewState = stakeState.update(filteredCompoundOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
        
        allOf(Coll(
            validCompounds,
            correctTotalStaked,
            correctSnapshot,
            correctNewState
        ))
        } else {
        true
        }
    }

    val validProfitShare = {
        if (transactionType==PROFIT_SHARE) {
        val ergProfit = plasmaStakingOutput.value - SELF.value
        val govProfit = plasmaStakingOutput.tokens(1)._2 - SELF.tokens(1)._2
        val correctErgProfit = ergProfit >= 0L && outputProfit(1) - profit(1) == ergProfit
        val correctGovProfit = govProfit >= 0L && outputProfit(0) - profit(0) == govProfit
        val correctUpdatedProfit = SELF.tokens.slice(2,SELF.tokens.size).zip(plasmaStakingOutput.tokens.slice(2,SELF.tokens.size)).forall{
            (io: ((Coll[Byte],Long),(Coll[Byte],Long))) =>
            val i = io._1
            val o = io._2
            val profitIndex = whiteListedTokenIds.indexOf(i._1,-1)
            val tokenProfit = o._2 - i._2
            allOf(Coll(
                i._1 == o._1,
                profitIndex >= 0,
                tokenProfit == outputProfit(profitIndex+2)-profit(profitIndex+2),
                tokenProfit >= 0L
            ))
        }
        val correctNewProfit = plasmaStakingOutput.tokens.slice(SELF.tokens.size,plasmaStakingOutput.tokens.size).forall{
            (o: (Coll[Byte],Long)) =>
            val profitIndex = whiteListedTokenIds.indexOf(o._1,-3)
            val tokenProfit = o._2
            allOf(Coll(
                profitIndex >= 0,
                tokenProfit == outputProfit(profitIndex+2),
                tokenProfit >= 0L
            ))
        }
        allOf(Coll(
            correctErgProfit,
            correctGovProfit,
            correctUpdatedProfit,
            correctNewProfit,
            plasmaStakingOutput.tokens.size >= SELF.tokens.size
        ))
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