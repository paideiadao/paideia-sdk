{

    /**
     *
     *  ActionSendFundsBasic
     *
     *  This action ensures that if the related proposal passes that the
     *  treasury sends funds to the outputs as defined at the time of proposal
     *  creation. 
     *  Any change is sent back to the treasury.
     *  If this action is to be repeated a copy is part of the output with 1
     *  repeat less.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaDaoKey             = _IM_PAIDEIA_DAO_KEY
    val imPaideiaDaoProposalTokenId = _IM_PAIDEIA_DAO_PROPOSAL_TOKENID
    val imPaideiaContractsTreasury  = _IM_PAIDEIA_CONTRACTS_TREASURY

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val sendFundsAction = SELF

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configInput   = CONTEXT.dataInputs(0)
    val proposalInput = CONTEXT.dataInputs(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTree = configInput.R4[AvlTree].get

    val sendFundsActionR4             = sendFundsAction.R4[Coll[Long]].get
    val sendFundsActionProposalIndex  = sendFundsActionR4(0)
    val sendFundsActionProposalOption = sendFundsActionR4(1)
    val sendFundsActionRepeats        = sendFundsActionR4(2)
    val sendFundsActionActivationTime = sendFundsActionR4(3)
    val sendFundsActionRepeatTime     = sendFundsActionR4(4)
    val sendFundsActionOutputs        = sendFundsAction.R5[Coll[Box]].get

    val proposalR4           = proposalInput.R4[Coll[Int]].get
    val proposalIndex        = proposalR4(0)
    val proposalPassedOption = proposalR4(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof = getVar[Coll[Byte]](0).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////
    
    val configValues = configTree.getMany(
        Coll(
            imPaideiaContractsTreasury
        ),
        configProof
    )

    val treasuryContractHash = configValues(0).get.slice(1,33)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Ensure the action is either correctly repeated or that the action     //
    // token is burned                                                       //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    // Check if the current action is being repeated
    val repeatedAction = sendFundsActionRepeats > 0L

    // Based on the repeatedAction flag, either repeat or burn the action
    val repeatOrBurn = if (repeatedAction) {
        // Create output box for repeated action and check its correctness
        val repeatOutput = OUTPUTS(sendFundsActionOutputs.size)
        allOf(Coll(
            repeatOutput.value                 == sendFundsAction.value,
            repeatOutput.tokens                == sendFundsAction.tokens,
            repeatOutput.R4[Coll[Long]].get(0) == sendFundsActionProposalIndex,
            repeatOutput.R4[Coll[Long]].get(1) == sendFundsActionProposalOption,
            repeatOutput.R4[Coll[Long]].get(2) == sendFundsActionRepeats - 1L,
            repeatOutput.R4[Coll[Long]].get(3) == sendFundsActionActivationTime + sendFundsActionRepeatTime,
            repeatOutput.R4[Coll[Long]].get(4) == sendFundsActionRepeatTime,
            repeatOutput.R5[Coll[Box]].get     == sendFundsActionOutputs,
            repeatOutput.propositionBytes      == sendFundsAction.propositionBytes
        ))
    } else {
        !(OUTPUTS.exists{
            (b: Box) =>
            b.tokens.exists{
                (token: (Coll[Byte],Long)) =>
                token._1 == sendFundsAction.tokens(0)._1
            }
        })
    }

    val changeBoxPresent: Boolean = 
        blake2b256(OUTPUTS(OUTPUTS.size-1).propositionBytes) == treasuryContractHash

    val minerO: Box = if (changeBoxPresent) {
        OUTPUTS(OUTPUTS.size-2)
    } else {
        OUTPUTS(OUTPUTS.size-1)
    }

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
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctConfig = configInput.tokens(0)._1 == imPaideiaDaoKey

    val correctProposal = allOf(Coll(
        proposalInput.tokens(0)._1  == imPaideiaDaoProposalTokenId,
        proposalIndex.toLong        == sendFundsActionProposalIndex,
        proposalPassedOption.toLong == sendFundsActionProposalOption
    ))

    val activationTimePassed = CONTEXT.preHeader.timestamp >= sendFundsActionActivationTime

    val correctOutput = sendFundsActionOutputs.zip(OUTPUTS.slice(0,sendFundsActionOutputs.size-1)).forall{
        (boxes: (Box,Box)) =>
        boxes._1.bytesWithoutRef == boxes._2.bytesWithoutRef
    }

    val correctOutputNumber = OUTPUTS.size == 
        sendFundsActionOutputs.size +
        (if (repeatedAction) 1 else 0) +
        (if (changeBoxPresent) 2 else 1)

    val noExtraBurn = countTokens(INPUTS) == countTokens(OUTPUTS) + (if (repeatedAction) 0L else 1L)

    val correctMinerOut: Boolean = allOf(Coll(
        minerO.value <= 5000000L,
        minerO.tokens.size == 0
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
        repeatOrBurn,
        correctOutput,
        correctOutputNumber,
        noExtraBurn,
        correctMinerOut
    )))
}
