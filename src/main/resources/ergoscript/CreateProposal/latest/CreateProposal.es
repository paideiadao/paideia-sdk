{

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

    val userProp: Coll[Byte] = createProposal.R4[Coll[Byte]].get

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

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        keyReturned,
        correctDaoOrigin
    )))
}