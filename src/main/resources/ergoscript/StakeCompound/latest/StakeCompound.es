/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def stakeCompound(imPaideiaDaoKey: Coll[Byte], stakeStateTokenId: Coll[Byte]) = {
    #import lib/emptyDigest/1.0.0/emptyDigest.es;
    #import lib/config/1.0.0/config.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/stakeRecord/1.0.0/stakeRecord.es;
    #import lib/box/1.0.0/box.es;

    /**
     *
     *  StakeCompound
     *
     *  Companion contract to the stake state contract containing the logic
     *  for handling compounding of stakes and distributing profit.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsStakingCompound: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = filterByTokenId((INPUTS, stakeStateTokenId))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val config: Box = filterByTokenId((CONTEXT.dataInputs, imPaideiaDaoKey))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    val compoundOperations: Coll[(Coll[Byte], Coll[Byte])] = 
        getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get

    val proof: Coll[Byte]                      = getVar[Coll[Byte]](2).get
    val snapshotProof: Coll[Byte]              = getVar[Coll[Byte]](3).get
    val removeProof: Coll[Byte]                = getVar[Coll[Byte]](4).get
    val snapshotParticipationProof: Coll[Byte] = getVar[Coll[Byte]](5).get
    val updateStakeProof: Coll[Byte]           = getVar[Coll[Byte]](6).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
        Coll(
            imPaideiaContractsStakingCompound
        ),
        configProof
    )

    val compoundContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeStateO: Box = filterByTokenId((OUTPUTS, stakeStateTokenId))(0)
    val compoundO: Box   = filterByHash((OUTPUTS, compoundContractHash))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val actualPPWeight: Byte = if (snapshotVoted(stakeState)(0) > 0)
            snapshotPureParticipationWeight(stakeState)(0).toByte
        else
            0.toByte

    val actualPWeight: Byte = if (snapshotVotesCast(stakeState)(0) > 0)
            snapshotParticipationWeight(stakeState)(0).toByte
        else
            0.toByte

    val totalParticipationWeight: Byte =
        actualPPWeight + actualPWeight

    val stakingWeight: Byte = 
        if (totalParticipationWeight > 0)
            max(
                100.toByte - totalParticipationWeight,
                0.toByte
            )
        else
            100.toByte

    val totalWeight: Byte = totalParticipationWeight + stakingWeight
    
    val notFound: Coll[Long]   = profit(stakeState).map{(l: Long) => -1L}

    val keys: Coll[Coll[Byte]] = compoundOperations.map{
        (kv: (Coll[Byte], Coll[Byte])) => kv._1
    }

    val filteredCompoundOperations: Coll[(Coll[Byte],Coll[Byte])] = 
        compoundOperations.filter{
            (kv: (Coll[Byte], Coll[Byte])) => 
            stakeRecordStake(kv._2) > 0
        }

    val currentStakeRecords: Coll[Option[Coll[Byte]]] = stakeTree(stakeState).getMany(keys,proof)

    val currentStakes: Coll[Coll[Long]] = currentStakeRecords
        .map{
            (b: Option[Coll[Byte]]) =>
            if (b.isDefined) {
                Coll(stakeRecordStake(b.get)) ++ stakeRecordProfits(b.get)
            } else {
                notFound
            }
        }

    val currentLocks: Coll[Long] = currentStakeRecords
        .map{
            (b: Option[Coll[Byte]]) =>
            if (b.isDefined) {
                stakeRecordLockedUntil(b.get)
            } else {
                -1L
            }
        }

    val snapshotStakes: Coll[Long] = snapshotTrees(stakeState)(0)._1.getMany(keys,snapshotProof)
        .map{
            (b: Option[Coll[Byte]]) => 
                stakeRecordStake(b.get)
        }

    val snapshotParticipation: Coll[(Long,Long)] = snapshotTrees(stakeState)(0)._2.getMany(
        keys,
        snapshotParticipationProof)
        .map{
            (b: Option[Coll[Byte]]) =>
            if (b.isDefined)
                (byteArrayToLong(
                    b.get.slice(0,8)
                ),
                byteArrayToLong(
                    b.get.slice(8,16)
                ))
            else
                (0L,0L)
        }

    val newStakes: Coll[Coll[Long]] = compoundOperations.map{
        (kv: (Coll[Byte], Coll[Byte])) => 
            Coll(stakeRecordStake(kv._2)) ++ stakeRecordProfits(kv._2)
    }

    val newLocks: Coll[Long] = compoundOperations.map{
        (kv: (Coll[Byte], Coll[Byte])) => 
            stakeRecordLockedUntil(kv._2)
    }

    val keyIndices: Coll[Int]      = keys.indices

    val rewards: Coll[(BigInt, Boolean)] = keyIndices.map{
            (index: Int) => 
            if (currentStakes(index)(0)>=0L) {
                val r = snapshotProfit(stakeState).map{
                    (p: Long) => 
                    (((snapshotStakes(index).toBigInt * p.toBigInt / snapshotStaked(stakeState)(0)) * stakingWeight) +
                    (if (actualPPWeight > 0) 
                        (snapshotParticipation(index)._1.toBigInt * p.toBigInt / snapshotVoted(stakeState)(0) * actualPPWeight)
                    else
                        0.toBigInt) +
                    (if (actualPWeight > 0)
                        (snapshotParticipation(index)._2.toBigInt * p.toBigInt / snapshotVotesCast(stakeState)(0) * actualPWeight)
                    else
                        0.toBigInt)) /
                    totalWeight
                }
                val newStake: Coll[BigInt] = currentStakes(index).zip(r).map{
                    (ll: (Long,BigInt)) => ll._1+ll._2
                }
                (r,newStake == newStakes(index).map{(s: Long) => s.toBigInt} && currentLocks(index) == newLocks(index))
            } else {
                (snapshotProfit(stakeState).map{(l: Long) => 0.toBigInt},true)
            }
        }

    val totalRewards: Coll[Long] = snapshotProfit(stakeState).indices.map{(i: Int) => 
        rewards.fold(0.toBigInt, {
            (z: BigInt, reward: (Coll[BigInt],Boolean)) => 
            z + reward._1(i)
        })}

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val validCompounds: Boolean = allOf(
        rewards.map{
            (reward: (Coll[BigInt],Boolean)) =>
            reward._2
        }
    )

    val correctTotalStaked: Boolean = 
        totalStaked(stakeState).toBigInt + totalRewards(0) == totalStaked(stakeStateO).toBigInt

    val correctSnapshot: Boolean = snapshotTrees(stakeState)(0)._1
        .remove(keys, removeProof).get.digest == snapshotTrees(stakeStateO)(0)._1.digest

    val correctNewState: Boolean = stakeTree(stakeState)
        .update(filteredCompoundOperations, updateStakeProof).get.digest == 
        stakeTree(stakeStateO).digest

    val minimumKeys: Boolean = keys.size >= 10 || snapshotTrees(stakeStateO)(0)._1.digest == emptyDigest

    val correctProfit: Boolean = profit(stakeState).indices.forall{(i: Int) =>
        profit(stakeStateO)(i) == profit(stakeState)(i) - totalRewards(i)
    }

    val correctUnchanged: Boolean = allOf(Coll(
        stakeStateO.value == stakeState.value,
        stakeStateO.tokens == stakeState.tokens,
        participationTree(stakeStateO).digest == participationTree(stakeState).digest,
        snapshotTrees(stakeStateO)(0)._2 == snapshotTrees(stakeState)(0)._2,
        snapshotTrees(stakeStateO).slice(1,snapshotTrees(stakeStateO).size) == 
            snapshotTrees(stakeState).slice(1,snapshotTrees(stakeState).size),
        nextEmission(stakeStateO) == nextEmission(stakeState),
        stakers(stakeStateO) == stakers(stakeState),
        votedThisCycle(stakeStateO) == votedThisCycle(stakeState),
        votesCastThisCycle(stakeStateO) == votesCastThisCycle(stakeState),
        snapshotValues(stakeStateO) == snapshotValues(stakeState),
        snapshotProfit(stakeStateO) == snapshotProfit(stakeState)
    ))

    val selfOutput: Boolean = allOf(Coll(
        blake2b256(compoundO.propositionBytes) == compoundContractHash,
        compoundO.value >= SELF.value
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        validCompounds,
        correctTotalStaked,
        correctSnapshot,
        correctNewState,
        selfOutput,
        correctUnchanged,
        minimumKeys,
        correctProfit
    )))
}