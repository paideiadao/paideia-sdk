/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def stake(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/stakeRecord/1.0.0/stakeRecord.es;

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

    val imPaideiaStakeStateTokenId: Coll[Byte] = 
        _IM_PAIDEIA_STAKING_STATE_TOKEN_ID

    val imPaideiaContractsStakeStake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_STAKE

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = INPUTS(0)
    val stake: Box      = SELF

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
    val stakeO: Box      = OUTPUTS(1)
    val userO: Box       = OUTPUTS(2)

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
            imPaideiaContractsStakeStake,
            imPaideiaStakeStateTokenId
        ),
        configProof
    )

    val stakeContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))
    val stakeStateTokenId: Coll[Byte] = bytearrayToTokenId(configValues(1))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val mintedKey: Coll[Byte] = userO.tokens(0)

    val lockedUntil: Long = stakeRecordLockedUntil(stakeOperations(0)._2)

    val stakeAmount: Long = stakeRecordStake(stakeOperations(0)._2)

    val profits: Coll[Long] = stakeRecordProfits(stakeOperations(0)._2)

    val updatedTree: AvlTree = stakeTree(stakeState).insert(stakeOperations, proof).get
        
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfigTokenId: Boolean = config.tokens(0)._1 == imPaideiaDaoKey

    val correctStakeState: Boolean = allOf(Coll(
        stakeState.tokens(0)._1 == stakeStateTokenId,
        stakeStateO.value >= stakeState.value,
        stakeStateO.tokens.slice(2,stakeStateO.tokens.size) == stakeState.tokens.slice(2,stakeState.tokens.size),
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

    val correctKeyMinted: Boolean = stakeState.id == mintedKey._1 && 
        stakeState.id == stakeOperations(0)._1
    
    val correctAmountMinted: Boolean = OUTPUTS.flatMap{(b: Box) => b.tokens}
        .fold(0L, {
            (z: Long, t: (Coll[Byte], Long)) => 
            z + (if (t._1 == mintedKey._1) 
                    t._2 
                else 
                    0L)
            }) == 1L

    val tokensStaked: Boolean = 
        stakeAmount == 
        (stakeStateO.tokens(1)._2 - stakeState.tokens(1)._2) && 
        stakeAmount == totalStaked(stakeStateO) - totalStaked(stakeState)

    val correctStakersCount: Boolean = stakers(stakeState) + 1L == stakers(stakeStateO)

    val singleStakeOp: Boolean = stakeOperations.size == 1

    val correctNewState: Boolean = updatedTree.digest == stakeTree(stakeStateO).digest

    val notLocked: Boolean = lockedUntil == 0L

    val selfOutput = allOf(Coll(
        blake2b256(stakeO.propositionBytes) == stakeContractHash,
        stakeO.value >= stake.value
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctConfigTokenId,
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