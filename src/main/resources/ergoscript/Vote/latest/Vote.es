{
    val proposalTokenId = _IM_PAIDEIA_DAO_PROPOSAL_TOKENID

    val proposalInput = INPUTS(0)

    val correctProposal = proposalInput.tokens(0)._1 == proposalTokenId

    val selfOut = OUTPUTS(1)

    val correctSelfOut = allOf(Coll(
        selfOut.propositionBytes == SELF.propositionBytes,
        selfOut.value >= SELF.value,
        selfOut.tokens == SELF.tokens,
        selfOut.R4[Long].get >= SELF.R4[Long].get,
        selfOut.R4[Long].get >= proposalInput.R5[Coll[Long]].get(0),
        selfOut.R5[Coll[Byte]].get == SELF.R5[Coll[Byte]].get
    ))

    sigmaProp(allOf(Coll(
        correctProposal,
        correctSelfOut
    )))
}