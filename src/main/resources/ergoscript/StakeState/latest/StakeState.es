/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def stakeState(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/txTypes/1.0.0/txTypes.es;
    #import lib/updateOrRefresh/1.0.0/updateOrRefresh.es;
    #import lib/box/1.0.0/box.es;

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

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val stakeState: Box = SELF

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
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](1).get
    val transactionType: Byte   = getVar[Byte](0).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    def validOutput(contractHash: Coll[Byte]): Boolean = allOf(Coll(
        blake2b256(stakeStateO.propositionBytes) == contractHash,
        stakeStateO.tokens(0) == stakeState.tokens(0),
        stakeStateO.tokens(1)._1 == stakeState.tokens(1)._1
    ))

    def transactionValidation(contractKey: Coll[Byte]): Boolean = {
        val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
            Coll(
                imPaideiaContractsStakeState,
                contractKey
            ),
            configProof
        )
        val stakingStateContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))
        val companionContractHash: Coll[Byte]    = bytearrayToContractHash(configValues(1))

        allOf(Coll(
            filterByHash((INPUTS,companionContractHash)).size == 1,
            validOutput(stakingStateContractHash)
        ))
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig: Boolean = config.tokens(0)._1 == imPaideiaDaoKey

    def validStake(txType: Byte): Boolean = if (txType == STAKE) {
        transactionValidation(imPaideiaContractsStakingStake) 
    } else 
            false

    def validUnstake(txType: Byte): Boolean = if (txType == UNSTAKE) transactionValidation(imPaideiaContractsStakingUnstake) else false

    def validChangeStake(txType: Byte): Boolean = if (txType == CHANGE_STAKE) transactionValidation(imPaideiaContractsStakingChangeStake) else false

    def validSnapshot(txType: Byte): Boolean = if (txType == SNAPSHOT) transactionValidation(imPaideiaContractsStakingSnapshot) else false

    def validCompound(txType: Byte): Boolean = if (txType == COMPOUND) transactionValidation(imPaideiaContractsStakingCompound) else false

    def validProfitShare(txType: Byte): Boolean = if (txType == PROFIT_SHARE) transactionValidation(imPaideiaContractsStakingProfitShare) else false

    def validVote(txType: Byte): Boolean = if (txType == VOTE) transactionValidation(imPaideiaContractsStakingVote) else false

    def validUpdate(txType: Byte): Boolean = if (txType == UPDATE) updateOrRefresh((imPaideiaContractsStakeState, config)) else false


    val validTx: Boolean = anyOf(Coll(
        validStake(transactionType),
        validUnstake(transactionType),
        validChangeStake(transactionType),
        validSnapshot(transactionType),
        validCompound(transactionType),
        validProfitShare(transactionType),
        validVote(transactionType),
        validUpdate(transactionType)
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctConfig,
        validTx
    )))
}