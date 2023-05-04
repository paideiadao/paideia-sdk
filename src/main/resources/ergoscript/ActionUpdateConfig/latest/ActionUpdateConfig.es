{

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

    val config        = INPUTS(0)
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

    val originalConfig = configInput.R4[AvlTree].get

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

    val configContractHash = configValues(0).get.slice(1,33)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Count number of tokens in a collection of boxes                       //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    def countTokens(boxes: Coll[Box]): Long = 
        boxes.flatMap{(b: Box) => b.tokens}.fold(0L, {
            (z: Long, token: (Coll[Byte], Long)) =>
            z + token._2
        })

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Apply deletes, updates and inserts to the config tree                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configAfterDelete = if (deleteActions.size > 0) originalConfig.remove(deleteActions, deleteProof).get else originalConfig
    val configAfterUpdate = if (updateActions.size > 0) configAfterDelete.update(updateActions, updateProof).get else configAfterDelete
    val configAfterInsert = if (insertActions.size > 0) configAfterUpdate.insert(insertActions, insertProof).get else configAfterUpdate

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig = configInput.tokens(0)._1 == imPaideiaDaoKey

    val correctProposal = allOf(Coll(
        proposalInput.tokens(0)._1       == imPaideiaDaoProposalTokenId,
        proposalInputIndex.toLong        == actionUpdateConfigProposalIndex,
        proposalInputPassedOption.toLong == actionUpdateConfigOptionIndex
    ))

    val activationTimePassed = CONTEXT.preHeader.timestamp >= actionUpdateConfigActivationTime

    val burnActionToken = !(OUTPUTS.exists{
            (b: Box) =>
            b.tokens.exists{
                (token: (Coll[Byte],Long)) =>
                token._1 == SELF.tokens(0)._1
            }
        })

    val correctOutputNumber = OUTPUTS.size == 2

    val noExtraBurn = countTokens(INPUTS) == countTokens(OUTPUTS) + 1L

    val correctConfigOutput = allOf(Coll(
        blake2b256(configOutput.propositionBytes) == configContractHash,
        configOutput.tokens                       == configInput.tokens,
        configOutput.value                        >= configInput.value,
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