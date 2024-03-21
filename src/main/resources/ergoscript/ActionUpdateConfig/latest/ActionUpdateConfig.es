{
    #import lib/bytearrayToContractHash/1.0.0/bytearrayToContractHash.es;
    #import lib/tokensInBoxesAll/1.0.0/tokensInBoxesAll.es;
    #import lib/tokenExists/1.0.0/tokenExists.es;
    /**
     *
     *  ActionUpdateConfig
     *
     *  This action ensures that if the related proposal passes that the
     *  dao config gets updated accordingly
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaDaoKey             = _IM_PAIDEIA_DAO_KEY
    val imPaideiaDaoProposalTokenId = _IM_PAIDEIA_DAO_PROPOSAL_TOKENID
    val imPaideiaContractsConfig    = _IM_PAIDEIA_CONTRACTS_CONFIG

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val config             = INPUTS(0)
    val actionUpdateConfig = SELF

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val proposal = CONTEXT.dataInputs(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configOutput = OUTPUTS(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val originalConfig = config.R4[AvlTree].get

    val proposalR4           = proposal.R4[Coll[Int]].get
    val proposalIndex        = proposalR4(0)
    val proposalPassedOption = proposalR4(1)

    val actionUpdateConfigR4             = actionUpdateConfig.R4[Coll[Long]].get
    val actionUpdateConfigProposalIndex  = actionUpdateConfigR4(0)
    val actionUpdateConfigOptionIndex    = actionUpdateConfigR4(1)
    val actionUpdateConfigActivationTime = actionUpdateConfigR4(3)

    val deleteActions = actionUpdateConfig.R5[Coll[Coll[Byte]]].get
    val updateActions = actionUpdateConfig.R6[Coll[(Coll[Byte], Coll[Byte])]].get
    val insertActions = actionUpdateConfig.R7[Coll[(Coll[Byte], Coll[Byte])]].get

    val outputConfig = configOutput.R4[AvlTree].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof = getVar[Coll[Byte]](0).get
    val deleteProof = getVar[Coll[Byte]](1).get
    val updateProof = getVar[Coll[Byte]](2).get
    val insertProof = getVar[Coll[Byte]](3).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues = configOutput.R4[AvlTree].get.getMany(
        Coll(
            imPaideiaContractsConfig
        ),
        configProof
    )

    val configContractHash = bytearrayToContractHash(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Apply deletes, updates and inserts to the config tree                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configAfterDelete = 
        if (deleteActions.size > 0) 
            originalConfig.remove(deleteActions, deleteProof).get 
        else 
            originalConfig
    val configAfterUpdate = 
        if (updateActions.size > 0) 
            configAfterDelete.update(updateActions, updateProof).get 
        else 
            configAfterDelete
    val configAfterInsert = 
        if (insertActions.size > 0) 
            configAfterUpdate.insert(insertActions, insertProof).get 
        else 
            configAfterUpdate

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig = config.tokens(0)._1 == imPaideiaDaoKey

    val correctProposal = allOf(Coll(
        proposal.tokens(0)._1       == imPaideiaDaoProposalTokenId,
        proposalIndex.toLong        == actionUpdateConfigProposalIndex,
        proposalPassedOption.toLong == actionUpdateConfigOptionIndex
    ))

    val activationTimePassed = CONTEXT.preHeader.timestamp >= actionUpdateConfigActivationTime

    val burnActionToken = !(tokenExists((OUTPUTS, SELF.tokens(0)._1)))

    val correctOutputNumber = OUTPUTS.size == 2

    val noExtraBurn = tokensInBoxesAll(INPUTS) == tokensInBoxesAll(OUTPUTS) + 1L

    val correctConfigOutput = allOf(Coll(
        blake2b256(configOutput.propositionBytes) == configContractHash,
        configOutput.tokens                       == config.tokens,
        configOutput.value                        >= config.value,
        outputConfig.digest                       == configAfterInsert.digest
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctConfig,
        correctProposal,
        activationTimePassed,
        correctConfigOutput,
        correctOutputNumber,
        noExtraBurn
    )))

}