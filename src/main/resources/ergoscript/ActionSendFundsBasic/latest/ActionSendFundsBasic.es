/**
 * This action ensures that if the related proposal passes that the
 * treasury sends funds to the outputs as defined at the time of proposal
 * creation. 
 * Any change is sent back to the treasury.
 * If this action is to be repeated a copy is part of the output with 1
 * repeat less.
 *
 * @param imPaideiaDaoKey Token ID of the dao config nft
 * @param imPaideiaDaoProposalTokenId Token ID of the dao proposal tokens
 *
 * @return
 */
@contract def actionSendFundsBasic(imPaideiaDaoKey: Coll[Byte], imPaideiaDaoProposalTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/bytesWithoutCreationInfo/1.0.0/bytesWithoutCreationInfo.es;
    #import lib/box/1.0.0/box.es;
    #import lib/actionSendFundsBasic/1.0.0/actionSendFundsBasic.es;
    #import lib/proposal/1.0.0/proposal.es;
    #import lib/garbageCollect/1.0.0/garbageCollect.es;
    #import lib/txTypes/1.0.0/txTypes.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsTreasury  = _IM_PAIDEIA_CONTRACTS_TREASURY

    val transactionType: Byte = getVar[Byte](0).get

    if (transactionType == GARBAGE_COLLECT) {
        sigmaProp(garbageCollect(Coll(aActionToken(SELF)._1)))
    } else {

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

    val configInput   = filterByTokenId((CONTEXT.dataInputs, imPaideiaDaoKey))(0)
    val proposalInput = filterByTokenId((CONTEXT.dataInputs, imPaideiaDaoProposalTokenId))(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof = getVar[Coll[Byte]](1).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////
    
    val configValues = configTree(configInput).getMany(
        Coll(
            imPaideiaContractsTreasury
        ),
        configProof
    )

    val treasuryContractHash = bytearrayToContractHash(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Ensure the action is either correctly repeated or that the action     //
    // token is burned                                                       //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    // Check if the current action is being repeated
    val repeatedAction = aRepeats(sendFundsAction) > 0L

    // Based on the repeatedAction flag, either repeat or burn the action
    val repeatOrBurn = if (repeatedAction) {
        // Create output box for repeated action and check its correctness
        val repeatOutput = OUTPUTS(sfaOutputs(sendFundsAction).size)
        allOf(Coll(
            repeatOutput.value            == sendFundsAction.value,
            repeatOutput.tokens           == sendFundsAction.tokens,
            aProposalIndex(repeatOutput)  == aProposalIndex(sendFundsAction),
            aProposalOption(repeatOutput) == aProposalOption(sendFundsAction),
            aRepeats(repeatOutput)        == aRepeats(sendFundsAction) - 1L,
            aActivationTime(repeatOutput) == aActivationTime(sendFundsAction) + aRepeatTime(sendFundsAction),
            aRepeatTime(repeatOutput)     == aRepeatTime(sendFundsAction),
            sfaOutputs(repeatOutput)      == sfaOutputs(sendFundsAction),
            repeatOutput.propositionBytes == sendFundsAction.propositionBytes
        ))
    } else {
        !(tokenExists((OUTPUTS, aActionToken(sendFundsAction)._1)))
    }

    //Check last output for presence of a change box
    val changeBoxPresent: Boolean = 
        blake2b256(OUTPUTS(OUTPUTS.size-1).propositionBytes) == treasuryContractHash

    val minerO: Box = if (changeBoxPresent) {
        OUTPUTS(OUTPUTS.size-2)
    } else {
        OUTPUTS(OUTPUTS.size-1)
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctProposal = allOf(Coll(
        pProposalToken(proposalInput)._1    == imPaideiaDaoProposalTokenId,
        pIndex(proposalInput).toLong        == aProposalIndex(sendFundsAction),
        pPassedOption(proposalInput).toLong == aProposalOption(sendFundsAction)
    ))

    val activationTimePassed = CONTEXT.preHeader.timestamp >= aActivationTime(sendFundsAction)

    val correctOutput = sfaOutputs(sendFundsAction).zip(OUTPUTS.slice(0,sfaOutputs(sendFundsAction).size)).forall{
        (boxes: (Box,Box)) =>
        bytesWithoutCreationInfo(boxes._1) == bytesWithoutCreationInfo(boxes._2)
    }

    val correctOutputNumber = OUTPUTS.size == 
        sfaOutputs(sendFundsAction).size +
        (if (repeatedAction) 1 else 0) +
        (if (changeBoxPresent) 2 else 1)

    //Make sure no tokens are burned (other than the action token)
    val noExtraBurn = tokensInBoxesAll(INPUTS) == tokensInBoxesAll(OUTPUTS) + (if (repeatedAction) 0L else 1L)

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
        correctProposal,
        activationTimePassed,
        repeatOrBurn,
        correctOutput,
        correctOutputNumber,
        noExtraBurn,
        correctMinerOut
    )))
    }
}
