/**
 * This action ensures that if the related proposal passes that the
 * dao config gets updated accordingly
 *
 * @param imPaideiaDaoKey Token ID of the dao key
 * @param imPaideiaDaoProposalTokenId Token ID of the dao proposal token
 *
 * @return
 */
@contract def actionUpdateConfig(imPaideiaDaoKey: Coll[Byte], imPaideiaDaoProposalTokenId: Coll[Byte]) = {
    #import lib/box/1.0.0/box.es;
    #import lib/proposal/1.0.0/proposal.es;
    #import lib/config/1.0.0/config.es;
    #import lib/actionUpdateConfig/1.0.0/actionUpdateConfig.es;
    #import lib/garbageCollect/1.0.0/garbageCollect.es;
    #import lib/txTypes/1.0.0/txTypes.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsConfig    = _IM_PAIDEIA_CONTRACTS_CONFIG

    val transactionType: Byte = getVar[Byte](0).get

    if (transactionType == GARBAGE_COLLECT) {
        sigmaProp(garbageCollect(Coll(aActionToken(SELF)._1)))
    } else {

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val config             = filterByTokenId((INPUTS, imPaideiaDaoKey))(0)
    val actionUpdateConfig = SELF

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val proposal = filterByTokenId((CONTEXT.dataInputs,imPaideiaDaoProposalTokenId))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configOutput = filterByTokenId((OUTPUTS, imPaideiaDaoKey))(0) 

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof = getVar[Coll[Byte]](1).get
    val deleteProof = getVar[Coll[Byte]](2).get
    val updateProof = getVar[Coll[Byte]](3).get
    val insertProof = getVar[Coll[Byte]](4).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues = configTree(configOutput).getMany(
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
        if (ucaDeletes(actionUpdateConfig).size > 0) 
            configTree(config).remove(ucaDeletes(actionUpdateConfig), deleteProof).get 
        else 
            configTree(config)
    val configAfterUpdate = 
        if (ucaUpdates(actionUpdateConfig).size > 0) 
            configAfterDelete.update(ucaUpdates(actionUpdateConfig), updateProof).get 
        else 
            configAfterDelete
    val configAfterInsert = 
        if (ucaInserts(actionUpdateConfig).size > 0) 
            configAfterUpdate.insert(ucaInserts(actionUpdateConfig), insertProof).get 
        else 
            configAfterUpdate

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctProposal = allOf(Coll(
        pProposalToken(proposal)._1    == imPaideiaDaoProposalTokenId,
        pIndex(proposal).toLong        == aProposalIndex(actionUpdateConfig),
        pPassedOption(proposal).toLong == aProposalOption(actionUpdateConfig)
    ))

    val activationTimePassed = CONTEXT.preHeader.timestamp >= aActivationTime(actionUpdateConfig)

    val burnActionToken = !(tokenExists((OUTPUTS, aActionToken(SELF)._1)))

    val correctOutputNumber = OUTPUTS.size == 2

    val correctInputNumber = INPUTS.size == 2

    val noExtraBurn = tokensInBoxesAll(INPUTS) == tokensInBoxesAll(OUTPUTS) + 1L

    val correctConfigOutput = allOf(Coll(
        blake2b256(configOutput.propositionBytes) == configContractHash,
        configOutput.tokens                       == config.tokens,
        configOutput.value                        >= config.value,
        configTree(configOutput).digest           == configAfterInsert.digest
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        correctInputNumber,
        correctProposal,
        activationTimePassed,
        burnActionToken,
        correctConfigOutput,
        correctOutputNumber,
        noExtraBurn
    )))

    }

}