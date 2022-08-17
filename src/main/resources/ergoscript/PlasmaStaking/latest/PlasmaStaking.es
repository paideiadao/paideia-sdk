{
  val stakeState = SELF.R4[AvlTree].get
  val emissionAmount = SELF.R5[Coll[Long]].get(0)
  val emissionDelay = SELF.R5[Coll[Long]].get(1).toInt
  val cycleLength = SELF.R5[Coll[Long]].get(2)
  val nextSnapshot = SELF.R5[Coll[Long]].get(3)
  val stakers = SELF.R5[Coll[Long]].get(4)
  val totalStaked = SELF.R5[Coll[Long]].get(5)
  val snapshotsStaked = SELF.R6[Coll[Long]].get
  val snapshotsTree = SELF.R7[Coll[AvlTree]].get

  val STAKE = 0.toByte
  val CHANGE_STAKE = 1.toByte
  val UNSTAKE = 2.toByte
  val SNAPSHOT = 3.toByte
  val COMPOUND = 4.toByte

  val emptyDigest = Coll(78,-58,31,72,91,-104,-21,-121,21,63,124,87,-37,79,94,-51,117,85,111,-35,-68,64,59,65,-84,-8,68,31,-34,-114,22,9,0).map{(i: Int) => i.toByte}

  val plasmaStakingOutput = OUTPUTS(0)

  val transactionType = getVar[Byte](0).get

  val validTransactionType = transactionType >= 0 && transactionType <= 5

  val validOutput = allOf(Coll(
    plasmaStakingOutput.propositionBytes == SELF.propositionBytes,
    plasmaStakingOutput.tokens(0) == SELF.tokens(0),
    plasmaStakingOutput.tokens(1)._1 == SELF.tokens(1)._1
  ))

  val validStake = {
    if (transactionType == STAKE) {
      val stakeOperations  = getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get
      val proof   = getVar[Coll[Byte]](2).get

      val userOutput = OUTPUTS(1)

      val stakeAmount = byteArrayToLong(stakeOperations(0)._2.slice(0,8))

      val correctKeyMinted = SELF.id == stakeOperations(0)._1 && SELF.id == userOutput.tokens(0)._1
      val correctAmountMinted = userOutput.tokens(0)._2 == 1

      val tokensStaked = stakeAmount == (plasmaStakingOutput.tokens(1)._2 - SELF.tokens(1)._2) && stakeAmount == plasmaStakingOutput.R5[Coll[Long]].get(5) - totalStaked

      val singleStakeOp = stakeOperations.size == 1

      val correctNewState = stakeState.insert(stakeOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
      
      allOf(Coll(
        correctKeyMinted,
        correctAmountMinted,
        tokensStaked,
        singleStakeOp,
        correctNewState
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

      val keyInOutput = userOutput.tokens(0)._1 == stakeOperations(0)._1

      val newStakeAmount = byteArrayToLong(stakeOperations(0)._2.slice(0,8))

      val currentStakeState = stakeState.get(stakeOperations(0)._1, proof).get

      val currentStakeAmount = byteArrayToLong(currentStakeState.slice(0,8))

      val tokensStaked = newStakeAmount - currentStakeAmount == (plasmaStakingOutput.tokens(1)._2 - SELF.tokens(1)._2) && newStakeAmount - currentStakeAmount == plasmaStakingOutput.R5[Coll[Long]].get(5) - totalStaked

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

      val userInput = INPUTS(1)

      val keyInInput = userInput.tokens(0)._1 == keys(0)

      val currentStakeState = stakeState.get(keys(0), proof).get

      val currentStakeAmount = byteArrayToLong(currentStakeState.slice(0,8))

      val tokensUnstaked = currentStakeAmount == (SELF.tokens(1)._2 - plasmaStakingOutput.tokens(1)._2) && currentStakeAmount == totalStaked - plasmaStakingOutput.R5[Coll[Long]].get(5)

      val singleStakeOp = keys.size == 1

      val correctNewState = stakeState.remove(keys, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
      
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

        val correctNewSnapshot = allOf(Coll(
          newSnapshotsStaked(newSnapshotsStaked.size-1) == totalStaked,
          newSnapshotsTrees(newSnapshotsTrees.size-1).digest == stakeState.digest
        ))
        
        val correctHistoryShift = allOf(Coll( 
                newSnapshotsTrees(0).digest == emptyDigest,
                newSnapshotsTrees.slice(0,emissionDelay-1) == newSnapshotsTrees.slice(1,emissionDelay),
                newSnapshotsStaked.slice(0,emissionDelay-1) == newSnapshotsStaked.slice(1,emissionDelay)
            ))

        val correctSize = newSnapshotsTrees.size.toLong == emissionDelay && newSnapshotsStaked.size.toLong == emissionDelay

        allOf(Coll(
          correctNewSnapshot,
          correctHistoryShift,
          correctSize
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
      val snapshotProof = if (getVar[Coll[Byte]](3).isDefined) getVar[Coll[Byte]](3).get else getVar[Coll[Byte]](2).get

      val keys = compoundOperations.map{(kv: (Coll[Byte], Coll[Byte])) => kv._1}

      val filteredCompoundOperations = compoundOperations.filter{(kv: (Coll[Byte], Coll[Byte])) => byteArrayToLong(kv._2) > 0}

      val currentStakes = stakeState.getMany(keys,proof)

      val snapshotStakes: Coll[Option[Coll[Byte]]] = snapshotsTree(0).getMany(keys,snapshotProof)

      val snapshotStaked = snapshotsStaked(0)

      val rewards = keys.map{
        (key: Coll[Byte]) =>
          val index = keys.indexOf(key,0)

          if (currentStakes(index).isDefined) {
            val snapshotStake = byteArrayToLong(snapshotStakes(index).get)
            (snapshotStake.toBigInt * emissionAmount.toBigInt / snapshotStaked).toLong
          } else {
            0L
          }
      }

      val validCompounds = keys.forall{
        (key: Coll[Byte]) =>
          val index = keys.indexOf(key,0)

          if (currentStakes(index).isDefined) {
            val currentStake = byteArrayToLong(currentStakes(index).get)
            val newStakeAmount = currentStake + rewards(index)
            
            newStakeAmount == byteArrayToLong(compoundOperations(index)._2)
          } else {
            snapshotStakes(index).isDefined
          }
      }

      //val totalRewards = rewards.fold(0L, {(z: Long, reward: Long) => z + reward})

      //val correctTotalStaked = totalStaked + totalRewards == plasmaStakingOutput.R5[Coll[Long]].get(5)

      //val correctSnapshot = snapshots(0)._2.remove(keys, snapshotProof).get.digest == plasmaStakingOutput.R6[Coll[(Long,AvlTree)]].get(0)._2.digest

      //val correctStakerCount = snapshots(0)._1 - keys.size == plasmaStakingOutput.R6[Coll[(Long,AvlTree)]].get(0)._1
      
      //val correctNewState = stakeState.update(filteredCompoundOperations, proof).get.digest == plasmaStakingOutput.R4[AvlTree].get.digest
      
      allOf(Coll(
        validCompounds,
        //correctTotalStaked,
        //correctSnapshot,
        //correctNewState
      ))
    } else {
      true
    }
  }

  sigmaProp(
    allOf(Coll(
      validTransactionType,
      validOutput,
      validStake,
      validChangeStake,
      validUnstake,
      validSnapshot,
      validCompound
    ))
  )
}