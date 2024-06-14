/**
 * This contract holds the configuration of the dao. It is usually used
 * as a data input and will only be updated if the dao votes on it in a
 * proposal
 *
 * @param imPaideiaDaoActionTokenId Token ID of the dao action token
 *
 * @return
 */
@contract def config(imPaideiaDaoActionTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/tokenExists/1.0.0/tokenExists.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

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
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configProof: Coll[Byte] = getVar[Coll[Byte]](0).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val configValues: Coll[Option[Coll[Byte]]] = configTree(configO).getMany(
        Coll(
            imPaideiaContractsConfig
        ),
        configProof
    )

    val configContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val tokensIntact: Boolean = config.tokens == configO.tokens
    val valueIntact: Boolean  = config.value <= configO.value

    val contractIntact: Boolean = blake2b256(configO.propositionBytes) == 
        configContractHash

    val validAction: Boolean = tokenExists((INPUTS, imPaideiaDaoActionTokenId))

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
