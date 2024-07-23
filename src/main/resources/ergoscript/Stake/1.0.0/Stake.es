/** 
 *  Companion contract to main stake state contract handling the create
 *  new stake logic
 *
 * @param imPaideiaDaoKey Token ID of the DAO key
 * @param stakeStateTokenId Token ID of the stake state nft
 *
 * @return
 */
@contract def stake(imPaideiaDaoKey: Coll[Byte], stakeStateTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/stakeRecord/1.0.0/stakeRecord.es;
    #import lib/box/1.0.0/box.es;

    /**
     *
     *  Stake
     *
     *  Companion contract to main stake state contract handling the create
     *  new stake logic
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsStakeStake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_STAKE

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = filterByTokenId((INPUTS, stakeStateTokenId))(0)
    val stake: Box      = SELF

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

    val stakeOperations: Coll[(Coll[Byte], Coll[Byte])] = 
        getVar[Coll[(Coll[Byte], Coll[Byte])]](1).get

    val proof: Coll[Byte] = getVar[Coll[Byte]](2).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
        Coll(
            imPaideiaContractsStakeStake
        ),
        configProof
    )

    val stakeContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeStateO: Box = filterByTokenId((OUTPUTS, stakeStateTokenId))(0)
    val stakeO: Box      = filterByHash((OUTPUTS,stakeContractHash))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val mintedKeyId: Coll[Byte] = INPUTS(0).id

    val lockedUntil: Long = stakeRecordLockedUntil(stakeOperations(0)._2)

    val stakeAmount: Long = stakeRecordStake(stakeOperations(0)._2)

    val profits: Coll[Long] = stakeRecordProfits(stakeOperations(0)._2)

    val updatedTree: AvlTree = stakeTree(stakeState).insert(stakeOperations, proof).get
        
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctStakeState: Boolean = allOf(Coll(
        stakeStateO.value               >= stakeState.value,
        otherTokens(stakeStateO)        == otherTokens(stakeState),
        participationTree(stakeStateO)  == participationTree(stakeState),
        nextEmission(stakeStateO)       == nextEmission(stakeState),
        votedThisCycle(stakeStateO)     == votedThisCycle(stakeState),
        votesCastThisCycle(stakeStateO) == votesCastThisCycle(stakeStateO),
        profit(stakeStateO)             == profit(stakeState),
        snapshotValues(stakeStateO)     == snapshotValues(stakeState),
        snapshotTrees(stakeStateO)      == snapshotTrees(stakeState),
        snapshotProfit(stakeStateO)     == snapshotProfit(stakeState)
    ))

    val zeroReward: Boolean = profits.forall{
        (l: Long) => l==0L
    }

    val correctKeyMinted: Boolean = mintedKeyId == stakeOperations(0)._1
    
    val correctAmountMinted: Boolean = tokensInBoxes((OUTPUTS, mintedKeyId)) == 1L

    val tokensStaked: Boolean = 
        stakeAmount == 
        (govToken(stakeStateO)._2 - govToken(stakeState)._2) && 
        stakeAmount == totalStaked(stakeStateO) - totalStaked(stakeState)

    val correctStakersCount: Boolean = stakers(stakeState) + 1L == stakers(stakeStateO)

    val singleStakeOp: Boolean = stakeOperations.size == 1

    val correctNewState: Boolean = updatedTree.digest == stakeTree(stakeStateO).digest

    val notLocked: Boolean = lockedUntil == 0L

    val selfOutput = stakeO.value >= stake.value

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctStakeState,
        correctKeyMinted,
        correctAmountMinted,
        tokensStaked,
        singleStakeOp,
        correctNewState,
        zeroReward,
        selfOutput,
        correctStakersCount,
        notLocked
    )))
}