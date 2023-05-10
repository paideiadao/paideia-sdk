{

    /**
     *
     *  Config
     *
     *  This contract holds the configuration of the dao. It is usually used
     *  as a data input and will only be updated if the dao votes on it in a
     *  proposal
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaDaoActionTokenId: Coll[Byte] = _IM_PAIDEIA_DAO_ACTION_TOKENID
    val imPaideiaContractsConfig: Coll[Byte]  = _IM_PAIDEIA_CONTRACTS_CONFIG

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val config: Box = SELF

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configO: Box = OUTPUTS(0)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configTreeO: AvlTree = configO.R4[AvlTree].get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTreeO.getMany(
        Coll(
            imPaideiaContractsConfig
        ),
        configProof
    )

    val configContractHash: Coll[Byte] = configValues(0).get.slice(1,33)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val tokensIntact: Boolean = config.tokens == configO.tokens
    val valueIntact: Boolean  = config.value <= configO.value

    val contractIntact: Boolean = blake2b256(configO.propositionBytes) == 
        configContractHash

    val validAction: Boolean = INPUTS.exists{
        (box: Box) =>
        if (box.tokens.size > 0) {
            box.tokens(0)._1 == imPaideiaDaoActionTokenId
        } else {
            false
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        tokensIntact,
        valueIntact,
        contractIntact,
        validAction
    )))

}
