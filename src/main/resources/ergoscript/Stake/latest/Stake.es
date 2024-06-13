/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def stake(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/bytearrayToTokenId/1.0.0/bytearrayToTokenId.es;
    #import lib/stakeRecordStake/1.0.0/stakeRecordStake.es;
    #import lib/stakeRecordProfits/1.0.0/stakeRecordProfits.es;
    #import lib/stakeRecordLockedUntil/1.0.0/stakeRecordLockedUntil.es;

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
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    val stakeStateTree: AvlTree    = stakeState.R4[Coll[AvlTree]].get(0)
    val participationTree: AvlTree = stakeState.R4[Coll[AvlTree]].get(1)
    val stakeStateR5: Coll[Long]   = stakeState.R5[Coll[Long]].get
    val nextEmission: Long         = stakeStateR5(0)
    val totalStaked: Long          = stakeStateR5(1)
    val stakers: Long              = stakeStateR5(2)
    val r5Rest: Coll[Long]         = stakeStateR5.slice(3, stakeStateR5.size)

    val stakeStateR6: Coll[Coll[Long]] = stakeState.R6[Coll[Coll[Long]]].get

    val stakeStateR7: Coll[(AvlTree, AvlTree)] = 
        stakeState.R7[Coll[(AvlTree, AvlTree)]].get

    val stakeStateR8: Coll[Long] = stakeState.R8[Coll[Long]].get

    val stakeStateTreeO: AvlTree    = stakeStateO.R4[Coll[AvlTree]].get(0)
    val participationTreeO: AvlTree = stakeStateO.R4[Coll[AvlTree]].get(1)
    val stakeStateOR5: Coll[Long]   = stakeStateO.R5[Coll[Long]].get
    val nextEmissionO: Long         = stakeStateOR5(0)
    val totalStakedO: Long          = stakeStateOR5(1)
    val stakersO: Long              = stakeStateOR5(2)
    val r5RestO: Coll[Long]         = stakeStateOR5.slice(3, stakeStateOR5.size)

    val stakeStateOR6: Coll[Coll[Long]] = stakeStateO.R6[Coll[Coll[Long]]].get

    val stakeStateOR7: Coll[(AvlTree, AvlTree)] = 
        stakeStateO.R7[Coll[(AvlTree, AvlTree)]].get

    val stakeStateOR8: Coll[Long] = stakeStateO.R8[Coll[Long]].get
    

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

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
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

    val updatedTree: AvlTree = stakeStateTree.insert(stakeOperations, proof).get
        
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
        participationTreeO == participationTree,
        nextEmissionO == nextEmission,
        r5RestO == r5Rest,
        stakeStateOR6 == stakeStateR6,
        stakeStateOR7 == stakeStateR7,
        stakeStateOR8 == stakeStateR8
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
        stakeAmount == totalStakedO - totalStaked

    val correctStakersCount: Boolean = stakers + 1L == stakersO

    val singleStakeOp: Boolean = stakeOperations.size == 1

    val correctNewState: Boolean = updatedTree.digest == stakeStateTreeO.digest

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