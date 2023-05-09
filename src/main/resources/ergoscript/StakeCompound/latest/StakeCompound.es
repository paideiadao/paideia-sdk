{

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

    val stakeStateTree: AvlTree     = stakeState.R4[Coll[AvlTree]].get(0)
    val stakeStateR5: Coll[Long]    = stakeState.R5[Coll[Long]].get
    val totalStaked: Long           = stakeStateR5(1)
    val snapshotsStaked: Coll[Long] = stakeState.R6[Coll[Long]].get

    val snapshotsTree: Coll[(AvlTree, AvlTree)] = 
        stakeState.R7[Coll[(AvlTree, AvlTree)]].get

    val snapshotsProfit: Coll[Coll[Long]] = stakeState.R8[Coll[Coll[Long]]].get

    val stakeStateOTree: AvlTree     = stakeStateO.R4[Coll[AvlTree]].get(0)
    val stakeStateOR5: Coll[Long]    = stakeStateO.R5[Coll[Long]].get
    val totalStakedO: Long           = stakeStateOR5(1)
    val snapshotsStakedO: Coll[Long] = stakeStateO.R6[Coll[Long]].get

    val snapshotsTreeO: Coll[(AvlTree, AvlTree)] = 
        stakeStateO.R7[Coll[(AvlTree, AvlTree)]].get
        
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    val compoundOperations: Coll[(Coll[Byte], Coll[Byte])] = 
        getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get

    val proof: Coll[Byte]         = getVar[Coll[Byte]](2).get
    val snapshotProof: Coll[Byte] = getVar[Coll[Byte]](3).get
    val removeProof: Coll[Byte]   = getVar[Coll[Byte]](4).get

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

    val stakeStateTokenId: Coll[Byte]    = configValues(0).get.slice(6,38)
    val compoundContractHash: Coll[Byte] = configValues(1).get.slice(1,33)
    val profitTokenIds: Coll[Byte]       = configValues(2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

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

    val snapshotStakes = snapshotsTree(0)._1.getMany(keys,snapshotProof)
        .map{
            (b: Option[Coll[Byte]]) => 
            longIndices.map{
                (i: Int) => 
                byteArrayToLong(
                    b.get.slice(i+stakeInfoOffset,i+8+stakeInfoOffset)
                )
            }
        }

    val newStakes: Coll[Coll[Long]] = compoundOperations.map{
        (kv: (Coll[Byte], Coll[Byte])) => 
        longIndices.map{
            (i: Int) => 
            byteArrayToLong(kv._2.slice(i+stakeInfoOffset,i+8+stakeInfoOffset))
        }
    }

    val snapshotStaked: Long       = snapshotsStaked(0)
    val snapshotProfit: Coll[Long] = snapshotsProfit(0)
    val keyIndices: Coll[Int]      = keys.indices

    val rewards: Coll[(BigInt, Boolean)] = keyIndices.map{
            (index: Int) => 
            if (currentStakes(index)(0)>=0L) {
                val r = snapshotProfit.map{
                    (p: Long) => 
                    (snapshotStakes(index)(0).toBigInt * p.toBigInt / snapshotStaked)
                }
                val newStake: Coll[BigInt] = currentStakes(index).zip(r).map{
                    (ll: (Long,BigInt)) => ll._1+ll._2
                }
                (r,newStake == newStakes(index).map{(s: Long) => s.toBigInt})
            } else {
                (snapshotProfit.map{(l: Long) => 0.toBigInt},true)
            }
        }

    val totalRewards: Long = rewards.fold(0.toBigInt, {
        (z: BigInt, reward: (Coll[BigInt],Boolean)) => 
        z + reward._1(0)
    })

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
        totalStaked.toBigInt + totalRewards == totalStakedO.toBigInt

    val correctSnapshot: Boolean = snapshotsTree(0)._1
        .remove(keys, removeProof).get.digest == snapshotsTreeO(0)._1.digest

    val correctNewState: Boolean = stakeStateTree
        .update(filteredCompoundOperations, proof).get.digest == 
        stakeStateOTree.digest

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
        selfOutput
    )))
}