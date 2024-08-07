/**
 * Ensures newly created DAOs are paying the correct fees to the paideia DAO
 *
 * @param paideiaDaoKey Token ID of the paideia dao key
 * @param paideiaTokenId Token ID of the paideia token
 *
 * @return
 */
@contract def paideiaOrigin(paideiaDaoKey: Coll[Byte], paideiaTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/box/1.0.0/box.es;
    #import lib/updateOrRefresh/1.0.0/updateOrRefresh.es;
    #import lib/txTypes/1.0.0/txTypes.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaFeesCreateDaoErg: Coll[Byte]  = _IM_PAIDEIA_FEES_CREATEDAO_ERG
    val imPaideiaFeesCreateDaoPai: Coll[Byte]  = _IM_PAIDEIA_FEES_CREATEDAO_PAIDEIA
    val imPaideiaContractsProtoDao: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_PROTODAO

    val imPaideiaContractsPaideiaOrigin: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_PAIDEIA_ORIGIN

    val imPaideiaContractsProtoDaoProxy: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_PROTODAOPROXY

    val imPaideiaContractsSplitProfit: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT

    def validProtoDaoCreation(txType: Byte) = {
        if (txType == CREATE_PROTO_DAO) {
            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Inputs                                                                //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val protoDAOProxy: Box = INPUTS(0)
            val origin: Box        = SELF

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Data Inputs                                                           //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val paideiaConfig: Box = filterByTokenId((CONTEXT.dataInputs, paideiaDaoKey))(0)

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Outputs                                                               //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val protoDaoO: Box    = OUTPUTS(0)
            val originO: Box      = OUTPUTS(1)
            val profitShareO: Box = OUTPUTS(2)

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Context variables                                                     //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](1).get

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // DAO Config value extraction                                           //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val configValues: Coll[Option[Coll[Byte]]] = configTree(paideiaConfig).getMany(
                Coll(
                    imPaideiaFeesCreateDaoErg,
                    imPaideiaFeesCreateDaoPai,
                    imPaideiaContractsProtoDao,
                    imPaideiaContractsProtoDaoProxy,
                    imPaideiaContractsSplitProfit
                ),
                paideiaConfigProof
            )

            val createDAOFeeErg: Long = byteArrayToLong(configValues(0).get.slice(1,9))

            val createDAOFeePaideia: Long = 
                byteArrayToLong(configValues(1).get.slice(1,9))

            val protoDAOContractHash: Coll[Byte]      = bytearrayToContractHash(configValues(2))
            val protoDAOProxyContractHash: Coll[Byte] = bytearrayToContractHash(configValues(3))
            val profitShareContractHash: Coll[Byte]   = bytearrayToContractHash(configValues(4))

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Simple conditions                                                     //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val correctPaideiaOrigin: Boolean = allOf(
                Coll(
                    originO.tokens(0)        == origin.tokens(0),
                    originO.tokens(1)._1     == origin.tokens(1)._1,
                    originO.tokens(1)._2     == origin.tokens(1)._2 - 1L,
                    originO.tokens.size      == 2,
                    originO.propositionBytes == origin.propositionBytes,
                    originO.value            >= origin.value
                )
            )

            val correctProtoOut: Boolean = allOf(
                Coll(
                    blake2b256(protoDaoO.propositionBytes) == protoDAOContractHash,
                    protoDaoO.tokens(0)._1 == origin.tokens(1)._1,
                    protoDaoO.tokens(0)._2 == 1L
                )
            )

            val correctPaideiaProfit: Boolean = allOf(
                Coll(
                    blake2b256(profitShareO.propositionBytes) == profitShareContractHash,
                    profitShareO.value == createDAOFeeErg + 1000000L,
                    profitShareO.tokens(0)._1 == paideiaTokenId,
                    profitShareO.tokens(0)._2 == createDAOFeePaideia,
                )
            )

            val correctProtoDaoProxy: Boolean = 
                blake2b256(protoDAOProxy.propositionBytes) == protoDAOProxyContractHash

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Final contract result                                                 //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            allOf(
                Coll(
                    correctPaideiaOrigin,
                    correctProtoOut,
                    correctPaideiaProfit,
                    correctProtoDaoProxy
                )
            )
        } else {
            false
        }
    }

    def validUpdateOrRefresh(txType: Byte): Boolean = {
        if (txType == UPDATE) {
            val config = filterByTokenId((CONTEXT.dataInputs, paideiaDaoKey))(0)
            updateOrRefresh((imPaideiaContractsPaideiaOrigin, config))
        } else {
            false
        }
    }

    val transactionType: Byte = getVar[Byte](0).get

    sigmaProp(anyOf(Coll(
        validProtoDaoCreation(transactionType),
        validUpdateOrRefresh(transactionType)
    )))
}