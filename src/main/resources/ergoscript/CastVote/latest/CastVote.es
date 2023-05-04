{
    val proposalInput = INPUTS(2)

    val userOutput = OUTPUTS(3)

    val voteKeyReturned = allOf(Coll(
        userOutput.tokens(0) == SELF.tokens(0),
        userOutput.propositionBytes == SELF.R6[Coll[Byte]].get
    ))

    val validProposal = proposalInput.tokens(0)._1 == _IM_PAIDEIA_DAO_PROPOSAL_TOKENID

    sigmaProp(allOf(Coll(
        voteKeyReturned,
        validProposal
    )))
}