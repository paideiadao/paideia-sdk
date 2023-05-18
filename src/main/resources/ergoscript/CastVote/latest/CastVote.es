{
    // Refund logic
    sigmaProp(
    if (INPUTS(0).id == SELF.id) {
        allOf(Coll(
            OUTPUTS(0).value >= SELF.value - 1000000L,
            OUTPUTS(0).tokens == SELF.tokens,
            OUTPUTS(0).propositionBytes == SELF.R6[Coll[Byte]].get,
            CONTEXT.preHeader.height >= SELF.creationInfo._1 + 30
        ))
    } else {
    /**
     *
     *  CastVote
     *
     *  This contract ensures the is added correctly to the proposal tally and
     *  the stake key is returned to the user.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaDaoProposalTokenId: Coll[Byte] = 
        _IM_PAIDEIA_DAO_PROPOSAL_TOKENID

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val castVote: Box = SELF
    val proposal: Box = INPUTS(2)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val userO: Box = OUTPUTS(3)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val proposalIndex: Int              = proposal.R4[Coll[Int]].get(0)

    val castVoteProposalIndex: Int       = castVote.R4[Int].get
    val userPropositionBytes: Coll[Byte] = castVote.R6[Coll[Byte]].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

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

    val validProposal: Boolean = allOf(Coll(
        proposalIndex == castVoteProposalIndex,
        proposal.tokens(0)._1 == imPaideiaDaoProposalTokenId
    ))

    val voteKeyReturned: Boolean = allOf(Coll(
        userO.tokens(0) == castVote.tokens(0),
        userO.propositionBytes == userPropositionBytes
    ))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    allOf(Coll(
        voteKeyReturned,
        validProposal
    ))})
}