{
    #import lib/emptyDigest/1.0.0/emptyDigest.es;
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;

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

    val daoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

    val imPaideiaStakingProfitTokenIds: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS

    val imPaideiaContractsStakingCompound: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val stakeInfoOffset: Int = 8

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = INPUTS(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val config: Box = CONTEXT.dataInputs(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeStateO: Box = OUTPUTS(0)
    val compoundO: Box   = OUTPUTS(1)
        
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateTree: AvlTree         = stakeState.R4[Coll[AvlTree]].get(0)
    val participationTree: AvlTree      = stakeState.R4[Coll[AvlTree]].get(1)
    val stakeStateR5: Coll[Long]        = stakeState.R5[Coll[Long]].get
    val nextEmission: Long              = stakeStateR5(0)
    val totalStaked: Long               = stakeStateR5(1)
    val r5Rest: Coll[Long]              = stakeStateR5.slice(2, 5)
    val stakeStateR6: Coll[Coll[Long]]  = stakeState.R6[Coll[Coll[Long]]].get
    val snapshotsStaked: Coll[Long]     = stakeStateR6(0)
    val snapshotsVoted: Coll[Long]      = stakeStateR6(1)
    val snapshotsVotedTotal: Coll[Long] = stakeStateR6(2)
    val snapshotsPPWeight: Coll[Long]   = stakeStateR6(3)
    val snapshotsPWeight: Coll[Long]    = stakeStateR6(4)

    val snapshotsTree: Coll[(AvlTree, AvlTree)] = 
        stakeState.R7[Coll[(AvlTree, AvlTree)]].get

    val stakeStateR8: Coll[Long] = stakeState.R8[Coll[Long]].get

    val snapshotProfit: Coll[Long] = stakeState.R8[Coll[Long]].get

    val stakeStateOTree: AvlTree        = stakeStateO.R4[Coll[AvlTree]].get(0)
    val participationTreeO: AvlTree     = stakeStateO.R4[Coll[AvlTree]].get(1)
    val stakeStateOR5: Coll[Long]       = stakeStateO.R5[Coll[Long]].get
    val nextEmissionO: Long             = stakeStateOR5(0)
    val totalStakedO: Long              = stakeStateOR5(1)
    val r5RestO: Coll[Long]             = stakeStateOR5.slice(2, 5)
    val profitO: Coll[Long]             = stakeStateOR5.slice(5, stakeStateOR5.size)
    val stakeStateOR6: Coll[Coll[Long]] = stakeStateO.R6[Coll[Coll[Long]]].get

    val snapshotsTreeO: Coll[(AvlTree, AvlTree)] = 
        stakeStateO.R7[Coll[(AvlTree, AvlTree)]].get
        
    val stakeStateOR8: Coll[Long] = stakeStateO.R8[Coll[Long]].get

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

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            imPaideiaStakeStateTokenId,
            imPaideiaContractsStakingCompound,
            imPaideiaStakingProfitTokenIds
        ),
        configProof
    )

    val stakeStateTokenId: Coll[Byte]    = bytearrayToTokenId(configValues(0))
    val compoundContractHash: Coll[Byte] = bytearrayToContractHash(configValues(1))
    val profitTokenIds: Coll[Byte]       = configValues(2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val actualPPWeight: Byte = if (snapshotsVoted(0) > 0)
            snapshotsPPWeight(0).toByte
        else
            0.toByte

    val actualPWeight: Byte = if (snapshotsVotedTotal(0) > 0)
            snapshotsPWeight(0).toByte
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

    val whiteListedTokenIds: Coll[Coll[Byte]] = 
        profitTokenIds.slice(0,(profitTokenIds.size-6)/37).indices.map{
            (i: Int) =>
            profitTokenIds.slice(6+(37*i)+5,6+(37*(i+1)))
        }

    val profit: Coll[Long] = stakeStateR5.slice(5,stakeStateR5.size).append(
        whiteListedTokenIds.slice(
            stakeStateR5.size-4,
            whiteListedTokenIds.size).map{
                (tokId: Coll[Byte]) => 0L
            }
        )
    
    val longIndices: Coll[Int] = profit.indices.map{(i: Int) => i*8}
    val notFound: Coll[Long]   = profit.map{(l: Long) => -1L}

    val keys: Coll[Coll[Byte]] = compoundOperations.map{
        (kv: (Coll[Byte], Coll[Byte])) => kv._1
    }

    val filteredCompoundOperations: Coll[(Coll[Byte],Coll[Byte])] = 
        compoundOperations.filter{
            (kv: (Coll[Byte], Coll[Byte])) => 
            byteArrayToLong(
                kv._2.slice(stakeInfoOffset,8+stakeInfoOffset)
            ) > 0
        }

    val currentStakes: Coll[Coll[Long]] = stakeStateTree.getMany(keys,proof)
        .map{
            (b: Option[Coll[Byte]]) =>
            if (b.isDefined) {
                longIndices.map{
                    (i: Int) => 
                    byteArrayToLong(
                        b.get.slice(i+stakeInfoOffset,i+8+stakeInfoOffset)
                    )
                }
            } else {
                notFound
            }
        }

    val snapshotStakes: Coll[Long] = snapshotsTree(0)._1.getMany(keys,snapshotProof)
        .map{
            (b: Option[Coll[Byte]]) => 
                byteArrayToLong(
                    b.get.slice(stakeInfoOffset,8+stakeInfoOffset)
                )
        }

    val snapshotParticipation: Coll[(Long,Long)] = snapshotsTree(0)._2.getMany(
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
        longIndices.map{
            (i: Int) => 
            byteArrayToLong(kv._2.slice(i+stakeInfoOffset,i+8+stakeInfoOffset))
        }
    }

    val snapshotStaked: Long       = snapshotsStaked(0)
    val keyIndices: Coll[Int]      = keys.indices

    val rewards: Coll[(BigInt, Boolean)] = keyIndices.map{
            (index: Int) => 
            if (currentStakes(index)(0)>=0L) {
                val r = snapshotProfit.map{
                    (p: Long) => 
                    (((snapshotStakes(index).toBigInt * p.toBigInt / snapshotStaked) * stakingWeight) +
                    (if (actualPPWeight > 0) 
                        (snapshotParticipation(index)._1.toBigInt * p.toBigInt / snapshotsVoted(0)) * snapshotsPPWeight(0)
                    else
                        0.toBigInt) +
                    (if (actualPWeight > 0)
                        (snapshotParticipation(index)._2.toBigInt * p.toBigInt / snapshotsVotedTotal(0)) * snapshotsPWeight(0)
                    else
                        0.toBigInt)) /
                    totalWeight
                }
                val newStake: Coll[BigInt] = currentStakes(index).zip(r).map{
                    (ll: (Long,BigInt)) => ll._1+ll._2
                }
                (r,newStake == newStakes(index).map{(s: Long) => s.toBigInt})
            } else {
                (snapshotProfit.map{(l: Long) => 0.toBigInt},true)
            }
        }

    val totalRewards: Coll[Long] = snapshotProfit.indices.map{(i: Int) => 
        rewards.fold(0.toBigInt, {
            (z: BigInt, reward: (Coll[BigInt],Boolean)) => 
            z + reward._1(i)
        })}

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

    val correctStakeState: Boolean = 
        stakeState.tokens(0)._1 == stakeStateTokenId

    val validCompounds: Boolean = allOf(
        rewards.map{
            (reward: (Coll[BigInt],Boolean)) =>
            reward._2
        }
    )

    val correctTotalStaked: Boolean = 
        totalStaked.toBigInt + totalRewards(0) == totalStakedO.toBigInt

    val correctSnapshot: Boolean = snapshotsTree(0)._1
        .remove(keys, removeProof).get.digest == snapshotsTreeO(0)._1.digest

    val correctNewState: Boolean = stakeStateTree
        .update(filteredCompoundOperations, updateStakeProof).get.digest == 
        stakeStateOTree.digest

    val minimumKeys: Boolean = keys.size >= 10 || snapshotsTreeO(0)._1.digest == emptyDigest

    val correctProfit: Boolean = profit.indices.forall{(i: Int) =>
        profitO(i) == profit(i) - totalRewards(i)
    }

    val correctUnchanged: Boolean = allOf(Coll(
        stakeStateO.value == stakeState.value,
        stakeStateO.tokens == stakeState.tokens,
        participationTreeO.digest == participationTree.digest,
        snapshotsTreeO(0)._2 == snapshotsTree(0)._2,
        snapshotsTreeO.slice(1,snapshotsTreeO.size) == snapshotsTree.slice(1,snapshotsTree.size),
        nextEmissionO == nextEmission,
        r5RestO == r5Rest,
        stakeStateOR6 == stakeStateR6,
        snapshotsTreeO.slice(1, snapshotsTreeO.size) == snapshotsTree.slice(1, snapshotsTree.size),
        snapshotsTreeO(0)._2 == snapshotsTree(0)._2,
        stakeStateOR8 == stakeStateR8
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
        correctConfig,
        correctStakeState,
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