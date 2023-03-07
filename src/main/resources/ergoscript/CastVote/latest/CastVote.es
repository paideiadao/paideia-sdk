{
    val proposalInput = INPUTS(0)

    val voteInput = INPUTS(1)

    val userOutput = OUTPUTS(2)

    val voteKeyReturned = allOf(Coll(
        userOutput.tokens == SELF.tokens,
        userOutput.propositionBytes == SELF.R6[Coll[Byte]].get
    ))

    val validProposal = proposalInput.tokens(0)._1 == _IM_PAIDEIA_DAO_PROPOSAL_TOKENID

    val validVote = voteInput.tokens(0)._1 == _IM_PAIDEIA_DAO_VOTE_TOKENID

    sigmaProp(allOf(Coll(
        voteKeyReturned,
        validProposal,
        validVote
    )))
}