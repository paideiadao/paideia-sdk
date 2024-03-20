{
    #import lib/validRefund/1.0.0/validRefund.es;

    // Refund logic
    sigmaProp(
    if (INPUTS(0).id == SELF.id) {
        validRefund((SELF, (OUTPUTS(0), (SELF.R4[Coll[Coll[Byte]]].get(0), 15))))
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

    def vlqByteSize(l: Long): Int = {
        if(l < 128L) 1
        else if(l < 16384L) 2
        else if(l < 2097152L) 3
        else if(l < 268435456L) 4
        else if(l < 34359738368L) 5
        else if(l < 4398046511104L) 6
        else if(l < 562949953421312L) 7
        else if(l < 72057594037927936L) 8
        else 9
    }

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
            (boxes: (Box, Box)) =>
            val valueByteSize = vlqByteSize(boxes._1.value)
            val creationByteSize1 = vlqByteSize(boxes._1.creationInfo._1.toLong)
            val creationByteSize2 = vlqByteSize(boxes._2.creationInfo._1.toLong)
            boxes._1.bytesWithoutRef.slice(0,valueByteSize+boxes._1.propositionBytes.size) == boxes._2.bytesWithoutRef.slice(0,valueByteSize+boxes._1.propositionBytes.size) &&
            boxes._1.bytesWithoutRef.slice(valueByteSize+boxes._1.propositionBytes.size+creationByteSize1, boxes._1.bytesWithoutRef.size) == boxes._2.bytesWithoutRef.slice(valueByteSize+boxes._1.propositionBytes.size+creationByteSize2, boxes._2.bytesWithoutRef.size)
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