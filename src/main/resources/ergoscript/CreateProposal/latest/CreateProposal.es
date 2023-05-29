{
    // Refund logic
    sigmaProp(
    if (INPUTS(0).id == SELF.id) {
        allOf(Coll(
            OUTPUTS(0).value >= SELF.value - 1000000L,
            OUTPUTS(0).tokens == SELF.tokens,
            OUTPUTS(0).propositionBytes == SELF.R4[Coll[Byte]].get,
            CONTEXT.preHeader.height >= SELF.creationInfo._1 + 30
        ))
    } else {
    /**
     *
     *  CreateProposal
     *
     *  Proxy contract to create a proposal and the related actions.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaDaoKey: Coll[Byte] = _IM_PAIDEIA_DAO_KEY

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoOrigin: Box      = INPUTS(0)
    val createProposal: Box = SELF

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoOriginKey: Coll[Byte] = daoOrigin.R4[Coll[Byte]].get

    val createProposalR4: Coll[Coll[Byte]] = 
        createProposal.R4[Coll[Coll[Byte]]].get

    val userProp: Coll[Byte]      = createProposalR4(0)
    val name: Coll[Byte]          = createProposalR4(1)
    val requestedBoxes: Coll[Box] = createProposal.R5[Coll[Box]].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate Calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val userO: Coll[Box] = OUTPUTS.filter{
        (b: Box) =>
        b.propositionBytes == userProp
    }

    val stakeKey: Coll[Byte] = createProposal.tokens(0)._1

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val correctDaoOrigin: Boolean = daoOriginKey == imPaideiaDaoKey

    val keyReturned: Boolean = anyOf(
        userO.flatMap{
            (b: Box) =>
            b.tokens
        }.map{
            (t: (Coll[Byte], Long)) =>
            t._1 == stakeKey
        }
    )

    val boxesCreated: Boolean = 
        requestedBoxes.zip(OUTPUTS.slice(1,requestedBoxes.size)).forall{
            (bb: (Box, Box)) =>
            bb._1.bytesWithoutRef == bb._2.bytesWithoutRef
        }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    allOf(Coll(
        keyReturned,
        correctDaoOrigin,
        boxesCreated
    ))})
}