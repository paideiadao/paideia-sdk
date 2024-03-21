{
    #import lib/validRefund/1.0.0/validRefund.es;

    // Refund logic
    sigmaProp(
    if (INPUTS(0).id == SELF.id) {
        validRefund((SELF, (OUTPUTS(0), (SELF.R6[Coll[Byte]].get, 15))))
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