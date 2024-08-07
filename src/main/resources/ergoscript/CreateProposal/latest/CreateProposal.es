/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 *
 * @return
 */
@contract def createProposal(imPaideiaDaoKey: Coll[Byte]) = {
    #import lib/validRefund/1.0.0/validRefund.es;
    #import lib/box/1.0.0/box.es;
    #import lib/bytesWithoutCreationInfo/1.0.0/bytesWithoutCreationInfo.es;

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

    val keyReturned: Boolean = tokenExists((userO, stakeKey))

    val boxesCreated: Boolean = 
        requestedBoxes.zip(OUTPUTS.slice(1,requestedBoxes.size)).forall{
            (boxes: (Box, Box)) =>
            bytesWithoutCreationInfo(boxes._1) == bytesWithoutCreationInfo(boxes._2)
        }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    allOf(Coll(
        keyReturned,
        true,
        // correctDaoOrigin,
        // boxesCreated
    ))})
}