{
    /**
     *
     *  StakeState
     *
     *  The main state contract. To reduce size and complexity most logic is
     *  stored in companion contracts.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

    val imPaideiaContractsStakeState: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_STATE

    val imPaideiaContractsStakingStake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_STAKE

    val imPaideiaContractsStakingChangeStake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_CHANGESTAKE

    val imPaideiaContractsStakingUnstake: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE

    val imPaideiaContractsStakingSnapshot: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT

    val imPaideiaContractsStakingCompound: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND

    val imPaideiaContractsStakingProfitShare: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_PROFITSHARE

    val imPaideiaContractsStakingVote: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_STAKING_VOTE

    val STAKE: Byte        = 0.toByte
    val CHANGE_STAKE: Byte = 1.toByte
    val UNSTAKE: Byte      = 2.toByte
    val SNAPSHOT: Byte     = 3.toByte
    val COMPOUND: Byte     = 4.toByte
    val PROFIT_SHARE: Byte = 5.toByte
    val VOTE: Byte         = 6.toByte

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = SELF
    val companion: Box  = INPUTS(1)

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

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree: AvlTree = config.R4[AvlTree].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get
    val transactionType: Byte   = getVar[Byte](1).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            imPaideiaContractsStakeState,
            imPaideiaContractsStakingStake,
            imPaideiaContractsStakingChangeStake,
            imPaideiaContractsStakingUnstake,
            imPaideiaContractsStakingSnapshot,
            imPaideiaContractsStakingCompound,
            imPaideiaContractsStakingProfitShare,
            imPaideiaContractsStakingVote
        ),
        configProof
    )

    val stakingStateContractHash: Coll[Byte] = configValues(0).get.slice(1,33)
    val stakeContractHash: Coll[Byte]        = configValues(1).get.slice(1,33)
    val changeStakeContractHash: Coll[Byte]  = configValues(2).get.slice(1,33)
    val unstakeContractHash: Coll[Byte]      = configValues(3).get.slice(1,33)
    val snapshotContractHash: Coll[Byte]     = configValues(4).get.slice(1,33)
    val compoundContractHash: Coll[Byte]     = configValues(5).get.slice(1,33)
    val profitShareContractHash: Coll[Byte]  = configValues(6).get.slice(1,33)
    val voteContractHash: Coll[Byte]         = configValues(7).get.slice(1,33)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

    val validTx: Boolean = anyOf(Coll(
        transactionType == STAKE && 
            blake2b256(companion.propositionBytes) == stakeContractHash,
        transactionType == CHANGE_STAKE && 
            blake2b256(companion.propositionBytes) == changeStakeContractHash,
        transactionType == UNSTAKE && 
            blake2b256(companion.propositionBytes) == unstakeContractHash,
        transactionType == SNAPSHOT && 
            blake2b256(companion.propositionBytes) == snapshotContractHash,
        transactionType == COMPOUND && 
            blake2b256(companion.propositionBytes) == compoundContractHash,
        transactionType == PROFIT_SHARE && 
            blake2b256(companion.propositionBytes) == profitShareContractHash,
        transactionType == VOTE && 
            blake2b256(companion.propositionBytes) == voteContractHash
    ))

    val validTransactionType: Boolean = 
        transactionType >= 0 && transactionType <= 6

    val validOutput: Boolean = allOf(Coll(
        blake2b256(stakeStateO.propositionBytes) == stakingStateContractHash,
        stakeStateO.tokens(0) == stakeState.tokens(0),
        stakeStateO.tokens(1)._1 == stakeState.tokens(1)._1
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctConfig,
        validTransactionType,
        validTx,
        validOutput
    )))
}