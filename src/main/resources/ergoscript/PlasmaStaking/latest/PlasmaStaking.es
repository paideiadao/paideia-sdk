{
  val stakeState = SELF.R4[AvlTree].get
  val emissionAmount = config.R4[Coll[Long]].get(1)
  val emissionDelay = config.R4[Coll[Long]].get(2).toInt
  val cycleLength = config.R4[Coll[Long]].get(3)
  val nextSnapshot = SELF.R5[Coll[Long]].get(0)
  val stakers = SELF.R5[Coll[Long]].get(1)
  val totalStaked = SELF.R5[Coll[Long]].get(2)
  val profit = SELF.R5[Coll[Long]].get.slice(3,SELF.R5[Coll[Long]].get.size)
  val snapshotsStaked = SELF.R6[Coll[Long]].get
  val snapshotsTree = SELF.R7[Coll[AvlTree]].get
  val snapshotsProfit = SELF.R8[Coll[Coll[Long]]].get

  val whiteListedProfit = config.R6[Coll[(Coll[Byte],Long)]].get
  val whiteListedTokenIds = whiteListedProfit.map{(token: (Coll[Byte],Long)) => token._1}

  val STAKE = 0.toByte
  val CHANGE_STAKE = 1.toByte
  val UNSTAKE = 2.toByte
  val SNAPSHOT = 3.toByte
  val COMPOUND = 4.toByte
  val PROFIT_SHARE = 5.toByte

  val emptyDigest = Coll(78,-58,31,72,91,-104,-21,-121,21,63,124,87,-37,79,94,-51,117,85,111,-35,-68,64,59,65,-84,-8,68,31,-34,-114,22,9,0).map{(i: Int) => i.toByte}
  
  val longIndices = profit.indices.map{(i: Int) => i*8}
  val notFound = profit.map{(l: Long) => -1L}
  
  val plasmaStakingOutput = OUTPUTS(0)

  val outputProfit = plasmaStakingOutput.R5[Coll[Long]].get.slice(3,plasmaStakingOutput.R5[Coll[Long]].get.size)

  val transactionType = getVar[Byte](0).get

  val validTransactionType = transactionType >= 0 && transactionType <= 5

  val validOutput = allOf(Coll(
    blake2b256(plasmaStakingOutput.propositionBytes) == config.R5[Coll[Byte]].get,
    plasmaStakingOutput.tokens(0) == SELF.tokens(0),
    plasmaStakingOutput.tokens(1)._1 == SELF.tokens(1)._1
  ))

  val validStake = {
    if (transactionType == STAKE) {
      val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get
      val proof   = getVar[Coll[Byte]](2).get

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
      val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get
      val proof   = getVar[Coll[Byte]](2).get

      val userOutput = OUTPUTS(1)

      val keyInOutput = userOutput.tokens.getOrElse(0,OUTPUTS(0).tokens(0))._1 == stakeOperations(0)._1

      val newStakeAmount = byteArrayToLong(stakeOperations(0)._2.slice(0,8))

      val currentStakeState = stakeState.get(stakeOperations(0)._1, proof).get

      val currentStakeAmount = byteArrayToLong(currentStakeState.slice(0,8))

      val tokensStaked = newStakeAmount - currentStakeAmount == (plasmaStakingOutput.tokens(1)._2 - SELF.tokens(1)._2) && newStakeAmount - currentStakeAmount == plasmaStakingOutput.R5[Coll[Long]].get(2) - totalStaked

      val singleStakeOp = stakeOperations.size == 1

      val correctNewState = stakeState.update(stakeOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
      
      allOf(Coll(
        keyInOutput,
        tokensStaked,
        singleStakeOp,
        correctNewState
      ))
    } else {
      true
    }
  }

  val validUnstake = {
    if (transactionType == UNSTAKE) {
      val keys  = getVar[Coll[(Coll[Byte],Coll[Byte])]](1).get.map{(kv: (Coll[Byte], Coll[Byte])) => kv._1}
      val proof   = getVar[Coll[Byte]](2).get
      val removeProof = getVar[Coll[Byte]](3).get

      val userInput = INPUTS(1)

      val keyInInput = userInput.tokens(0)._1 == keys(0)

      val currentStakeState = stakeState.get(keys(0), proof).get

      val currentStakeAmount = byteArrayToLong(currentStakeState.slice(0,8))

      val tokensUnstaked = currentStakeAmount == (SELF.tokens(1)._2 - plasmaStakingOutput.tokens(1)._2) && currentStakeAmount == totalStaked - plasmaStakingOutput.R5[Coll[Long]].get(2)

      val singleStakeOp = keys.size == 1

      val correctNewState = stakeState.remove(keys, removeProof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
      
      allOf(Coll(
        keyInInput,
        tokensUnstaked,
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
          newSnapshotsProfit(newSnapshotsProfit.size-1).slice(1,profit.size) == profit.slice(1,profit.size),
          newSnapshotsProfit(newSnapshotsProfit.size-1)(0) == profit(0) + min(emissionAmount,SELF.tokens(1)._2-totalStaked-profit(0))
        ))
        
        val correctHistoryShift = allOf(Coll( 
                newSnapshotsTrees(0).digest == emptyDigest,
                newSnapshotsTrees.slice(0,emissionDelay-1) == newSnapshotsTrees.slice(1,emissionDelay),
                newSnapshotsStaked.slice(0,emissionDelay-1) == newSnapshotsStaked.slice(1,emissionDelay)
            ))

        val profitReset = outputProfit.forall{(p: Long) => p==0L}

        val correctSize = newSnapshotsTrees.size.toLong == emissionDelay && 
                          newSnapshotsStaked.size.toLong == emissionDelay &&
                          newSnapshotsProfit.size.toLong == emissionDelay

        allOf(Coll(
          correctNewSnapshot,
          correctHistoryShift,
          correctSize,
          profitReset
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
      val newStakes: Coll[Coll[Long]] = compoundOperations.map{(kv: (Coll[Byte], Coll[Byte])) => notFound}//longIndices.map{(i: Int) => byteArrayToLong(kv._2.slice(i,i+8))}}
      val snapshotStaked = snapshotsStaked(0)

      val snapshotProfit = snapshotsProfit(0)

      val keyIndices = keys.indices

      val rewards = keyIndices.map{
        (index: Int) => {
          if (currentStakes(index)(0)>=0L) {
            snapshotProfit.map{(p: Long) => (snapshotStakes(index)(0) * p / snapshotStaked)}
          } else {
            snapshotProfit.map{(l: Long) => 0L}
          }
        }
      }

      val validCompounds = allOf(keyIndices.map{
        (index: Int) =>
          if (currentStakes(index)(0)>=0L) {
            val currentStake: Coll[Long] = currentStakes(index)
            val reward: Coll[Long] = rewards(index)
            val newStake: Coll[Long] = currentStake.zip(reward).map{(ll: (Long,Long)) => ll._1+ll._2}
            
            newStake == newStakes(index)
          } else {
            true
          }
      })

      val totalRewards = rewards.fold(0L, {(z: Long, reward: Coll[Long]) => z + reward(0)})

      val correctTotalStaked = totalStaked + totalRewards == plasmaStakingOutput.R5[Coll[Long]].get(2)

      val correctSnapshot = snapshotsTree(0).remove(keys, removeProof).get.digest == plasmaStakingOutput.R7[Coll[AvlTree]].get(0).digest
      
      val correctNewState = stakeState.update(filteredCompoundOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
      
      allOf(Coll(
        //validCompounds,
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
          tokenProfit == outputProfit(profitIndex)-profit(profitIndex),
          tokenProfit >= 0L
        ))
      }
      val correctNewProfit = plasmaStakingOutput.tokens.slice(SELF.tokens.size,plasmaStakingOutput.tokens.size).forall{
        (o: (Coll[Byte],Long)) =>
        val profitIndex = whiteListedTokenIds.indexOf(o._1,-1)
        val tokenProfit = o._2
        allOf(Coll(
          profitIndex >= 0,
          tokenProfit == outputProfit(profitIndex)-profit(profitIndex),
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