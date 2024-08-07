/**
 * This is a long living contract that guards the configuration of the DAO. 
 * It can only be spent when the contract is updated, prevent storage rent 
 * or when a proposal to change the configuration has passed.
 *
 * @param imPaideiaDaoActionTokenId Token ID of the dao action token
 *
 * @return
 */
@contract def config(imPaideiaDaoActionTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/box/1.0.0/box.es;
    #import lib/updateOrRefresh/1.0.0/updateOrRefresh.es;
    #import lib/txTypes/1.0.0/txTypes.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsConfig: Coll[Byte]  = _IM_PAIDEIA_CONTRACTS_CONFIG
    val imPaideiaContractsAction: Coll[Byte]  = _IM_PAIDEIA_CONTRACTS_ACTION

    def validChangeConfigTransaction(txType: Byte): Boolean = {
        if (txType == CHANGE_CONFIG) {

        ///////////////////////////////////////////////////////////////////////////
        //                                                                       //
        // Inputs                                                                //
        //                                                                       //
        ///////////////////////////////////////////////////////////////////////////

        val config: Box = SELF
        val action: Box = filterByTokenId((INPUTS, imPaideiaDaoActionTokenId))(0)

        ///////////////////////////////////////////////////////////////////////////
        //                                                                       //
        // Outputs                                                               //
        //                                                                       //
        ///////////////////////////////////////////////////////////////////////////

        val configO: Box = filterByTokenId((OUTPUTS,configDaoKey(config)))(0)

        ///////////////////////////////////////////////////////////////////////////
        //                                                                       //
        // Context variables                                                     //
        //                                                                       //
        ///////////////////////////////////////////////////////////////////////////

        val configProof: Coll[Byte] = getVar[Coll[Byte]](1).get

        ///////////////////////////////////////////////////////////////////////////
        //                                                                       //
        // DAO Config value extraction                                           //
        //                                                                       //
        ///////////////////////////////////////////////////////////////////////////

        val configValues: Coll[Option[Coll[Byte]]] = configTree(configO).getMany(
            Coll(
                imPaideiaContractsConfig,
                blake2b256(imPaideiaContractsAction++action.propositionBytes)
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

        val validAction: Boolean = configValues(1).isDefined

        ///////////////////////////////////////////////////////////////////////////
        //                                                                       //
        // Final contract result                                                 //
        //                                                                       //
        ///////////////////////////////////////////////////////////////////////////

        allOf(Coll(
            tokensIntact,
            valueIntact,
            contractIntact,
            validAction
        ))
        } else {
            false
        }
    }

    val transactionType: Byte = getVar[Byte](0).get

    sigmaProp(anyOf(Coll(
        validChangeConfigTransaction(transactionType),
        transactionType == UPDATE && updateOrRefresh((imPaideiaContractsConfig, SELF))
    )))

}
