{
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;
    
    /**
     *
     *  ChangeStake
     *
     *  This contract is a companion contract to the main stake contract.
     *  It ensures the stake is changed correctly following the rules.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaDaoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

    val imPaideiaContractsStakingChangeStake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_CHANGESTAKE

    val imPaideiaStakingStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val imPaideiaStakingProfitTokenIds: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_PROFIT_TOKENIDS

    val stakeInfoOffset = 8

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val changeStake: Box = SELF
    val stakeState: Box  = INPUTS(0)

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

    val stakeStateO: Box  = OUTPUTS(0)
    val changeStakeO: Box = OUTPUTS(1)
    val userO: Box        = OUTPUTS(2)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateTree: AvlTree  = stakeState.R4[Coll[AvlTree]].get(0)
    val stakeStateR5: Coll[Long] = stakeState.R5[Coll[Long]].get
    val nextEmission: Long       = stakeStateR5(0)
    val totalStaked: Long        = stakeStateR5(1)
    val stakers: Long            = stakeStateR5(2)
    val voted: Long              = stakeStateR5(3)
    val votedTotal: Long         = stakeStateR5(4)
    val stakeStateR6: Coll[Coll[Long]] = 
        stakeState.R6[Coll[Coll[Long]]].get

    val stakeStateR7: Coll[(AvlTree,AvlTree)] = 
        stakeState.R7[Coll[(AvlTree,AvlTree)]].get

    val stakeStateR8: Coll[Long] = stakeState.R8[Coll[Long]].get
    val participationTree: AvlTree     = stakeState.R4[Coll[AvlTree]].get(1)

    val stakeStateTreeO: AvlTree  = stakeStateO.R4[Coll[AvlTree]].get(0)
    val stakeStateOR5: Coll[Long] = stakeStateO.R5[Coll[Long]].get
    val nextEmissionO: Long       = stakeStateOR5(0)
    val totalStakedO: Long        = stakeStateOR5(1)
    val stakersO: Long            = stakeStateOR5(2)
    val votedO: Long              = stakeStateOR5(3)
    val votedTotalO: Long         = stakeStateOR5(4)
    val profitO: Coll[Long]       = stakeStateOR5.slice(5,stakeStateOR5.size)
    val stakeStateOR6: Coll[Coll[Long]] = 
        stakeStateO.R6[Coll[Coll[Long]]].get

    val stakeStateOR7: Coll[(AvlTree,AvlTree)] = 
        stakeStateO.R7[Coll[(AvlTree,AvlTree)]].get

    val stakeStateOR8: Coll[Long] = stakeStateO.R8[Coll[Long]].get
    val participationTreeO: AvlTree     = stakeStateO.R4[Coll[AvlTree]].get(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    val stakeOperations: Coll[(Coll[Byte], Coll[Byte])]  = 
        getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get

    val proof: Coll[Byte] = getVar[Coll[Byte]](2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            imPaideiaContractsStakingChangeStake,
            imPaideiaStakingStateTokenId,
            imPaideiaStakingProfitTokenIds
        ),
        configProof
    )

    val changeStakeContractSignature: Coll[Byte] = bytearrayToContractHash(configValues(0))
    val stakingStakeTokenId: Coll[Byte]          = bytearrayToTokenId(configValues(1))
    val profitTokenIds: Coll[Byte]               = configValues(2).get

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

    // Append 0's if the dao has added new whitelistedtokens
    val profit: Coll[Long] = stakeStateR5.slice(5,stakeStateR5.size)
        .append(
            whiteListedTokenIds.slice(
                stakeStateR5.size-4,
                whiteListedTokenIds.size
            ).map{(tokId: Coll[Byte]) => 0L})

    val longIndices: Coll[Int] = profit.indices.map{(i: Int) => i*8}
    
    val currentStakeState: Coll[Option[Coll[Byte]]] = 
        stakeStateTree.get(stakeOperations(0)._1, proof).get

    val currentProfits: Coll[Long] = longIndices.map{
        (i: Int) => 
        byteArrayToLong(
            currentStakeState.slice(i+stakeInfoOffset,i+8+stakeInfoOffset)
        )
    }

    val newProfits: Coll[Long] = longIndices.map{
        (i: Int) => 
        byteArrayToLong(
            stakeOperations(0)._2.slice(i+stakeInfoOffset,i+8+stakeInfoOffset)
        )
    }

    val combinedProfit: Coll[(Long, Long)] = currentProfits.zip(newProfits)

    val currentStakeAmount: Long = currentProfits(0)
    val newStakeAmount: Long     = newProfits(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfigTokenId: Boolean = config.tokens(0)._1 == imPaideiaDaoKey

    val selfOutput: Boolean = allOf(Coll(
        blake2b256(changeStakeO.propositionBytes) == changeStakeContractSignature,
        changeStakeO.value >= changeStake.value
    ))

    val correctStakeState: Boolean = stakeState.tokens(0)._1 == stakingStakeTokenId

    val keyInOutput: Boolean = userO.tokens(0)._1 == stakeOperations(0)._1

    val tokensStaked: Boolean = newStakeAmount - currentStakeAmount == 
        (stakeStateO.tokens(1)._2 - stakeState.tokens(1)._2) && 
        newStakeAmount - currentStakeAmount == 
        totalStakedO - totalStaked

    val noPartialUnstake: Boolean = newStakeAmount >= currentStakeAmount

    val singleStakeOp: Boolean = stakeOperations.size == 1

    val correctNewState: Boolean = 
        stakeStateTree.update(stakeOperations, proof).get.digest == 
        stakeStateTreeO.digest

    val noAddedOrNegativeProfit: Boolean = 
        combinedProfit.slice(1,combinedProfit.size).forall{
            (p: (Long, Long)) => p._1 >= p._2 && p._2 >= 0L
        }
    
    val correctErgProfit: Boolean = currentProfits(1) - newProfits(1) == 
        stakeState.value - stakeStateO.value

    val correctTokenProfit: Boolean = 
        stakeState.tokens.slice(2,stakeState.tokens.size).forall{
            (token: (Coll[Byte], Long)) =>
            val profitIndex: Int = whiteListedTokenIds.indexOf(token._1,-3)
            val tokenAmountInOutput: Long = stakeStateO.tokens.fold(0L, {
                (z: Long, outputToken: (Coll[Byte], Long)) => 
                if (outputToken._1 == token._1) z + outputToken._2 else z}
            )
            token._2 - tokenAmountInOutput == 
                currentProfits(profitIndex+2) - newProfits(profitIndex+2)
        }

    val unchangedRegisters: Boolean = allOf(Coll(
        participationTreeO == participationTree,
        profitO == profit,
        nextEmissionO == nextEmission,
        stakersO == stakers,
        votedO == voted,
        votedTotalO == votedTotal,
        stakeStateOR6 == stakeStateR6,
        stakeStateOR7 == stakeStateR7,
        stakeStateOR8 == stakeStateR8
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
        correctStakeState,
        keyInOutput,
        tokensStaked,
        singleStakeOp,
        correctNewState,
        noAddedOrNegativeProfit,
        correctErgProfit,
        correctTokenProfit,
        selfOutput,
        unchangedRegisters,
        noPartialUnstake
    )))
}