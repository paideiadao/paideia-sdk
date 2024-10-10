/**
 * Holds the dao token verifying it as a genuine Paideia DAO and
 * controls the proposal/action creation process.
 *
 * @param imPaideiaDaoKey Token ID of the dao key
 * @param paideiaDaoKey Token ID of the paideia dao key
 * @param paideiaTokenId Token ID of the paideia token
 * @param stakeStateTokenId Token ID of the stake state nft
 *
 * @return
 */
@contract def daoOrigin(imPaideiaDaoKey: Coll[Byte], paideiaDaoKey: Coll[Byte], paideiaTokenId: Coll[Byte], stakeStateTokenId: Coll[Byte]) = {
    #import lib/maxLong/1.0.0/maxLong.es;
    #import lib/config/1.0.0/config.es;
    #import lib/proposal/1.0.0/proposal.es;
    #import lib/action/1.0.0/action.es;
    #import lib/daoOrigin/1.0.0/daoOrigin.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/stakeRecord/1.0.0/stakeRecord.es;
    #import lib/box/1.0.0/box.es;
    #import lib/updateOrRefresh/1.0.0/updateOrRefresh.es;
    #import lib/txTypes/1.0.0/txTypes.es;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val imPaideiaContractsDao: Coll[Byte]      = _IM_PAIDEIA_CONTRACTS_DAO
    val imPaideiaContractsProposal: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_PROPOSAL
    val imPaideiaContractsAction: Coll[Byte]   = _IM_PAIDEIA_CONTRACTS_ACTION

    val imPaideiaFeesCreateProposalPaideia: Coll[Byte] =  
        _IM_PAIDEIA_FEES_CREATEPROPOSAL_PAIDEIA

    val imPaideiaDaoMinProposalTime: Coll[Byte] = 
        _IM_PAIDEIA_DAO_MIN_PROPOSAL_TIME

    val imPaideiaDaoMinStakeProposal: Coll[Byte] = _IM_PAIDEIA_DAO_MIN_STAKE_PROPOSAL

    val transactionType: Byte          = getVar[Byte](0).get

    def validCreateProposalTransaction(txType: Byte): Boolean = {
        if (txType == CREATE_PROPOSAL) {
            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Inputs                                                                //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val daoOrigin: Box      = SELF

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Data Inputs                                                           //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val paideiaConfig: Box = filterByTokenId((CONTEXT.dataInputs, paideiaDaoKey))(0)
            val config: Box        = filterByTokenId((CONTEXT.dataInputs, imPaideiaDaoKey))(0)
            val stakeState: Box    = filterByTokenId((CONTEXT.dataInputs, stakeStateTokenId))(0)

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Outputs                                                               //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val daoOriginO: Box = OUTPUTS(0)
            val proposalO: Box  = OUTPUTS(1)
            
            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Context variables                                                     //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////
            val collBytesVars: Coll[Coll[Byte]] = getVar[Coll[Coll[Byte]]](1).get
            val paideiaConfigProof: Coll[Byte] = collBytesVars(0)
            val configProof: Coll[Byte]        = collBytesVars(1)
            val proposalBox: Box               = getVar[Box](2).get
            val actionBoxes: Coll[Box]         = getVar[Coll[Box]](3).get
            val stakeKey: Coll[Byte]           = collBytesVars(2)
            val stakeProof: Coll[Byte]         = collBytesVars(3)

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // DAO Config value extraction                                           //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
                configTree(paideiaConfig).getMany(
                    Coll(
                        imPaideiaFeesCreateProposalPaideia
                    ),
                    paideiaConfigProof
                )

            val createProposalFee: Long = bytearrayToLongClamped((paideiaConfigValues(0),(0L,(100000000000L, 1000L))))

            val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
                Coll(
                    imPaideiaContractsDao,
                    imPaideiaDaoMinProposalTime,
                    imPaideiaDaoMinStakeProposal,
                    blake2b256(imPaideiaContractsProposal++proposalBox.propositionBytes)
                )++actionBoxes.map{
                    (box: Box) =>
                    blake2b256(imPaideiaContractsAction++box.propositionBytes)
                },
                configProof
            )

            val daoOriginContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))
            //Min 12 hours, max 1 month, default 24 hours
            val minProposalTime: Long = bytearrayToLongClamped((configValues(1),(43200000L,(2626560000L,86400000L))))
            //Minimum amount staked to be able to create a proposal. Should not be higher than total staked amount to avoid locking the DAO
            val minStakeAmount: Long = bytearrayToLongClamped((configValues(2),(0L, (totalStaked(stakeState)/2L,0L))))
            val proposalContractHash: Coll[Byte] = bytearrayToContractHash(configValues(3))
            val actionContractHashes: Coll[Coll[Byte]] = 
                configValues.slice(4,configValues.size).map{
                    (cv: Option[Coll[Byte]]) =>
                    bytearrayToContractHash(cv)
                }

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Intermediate calculations                                             //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val proposalId: Long = maxLong - daoProposalToken(daoOrigin)._2

            val actionOutputs: Coll[Box] = OUTPUTS.slice(2,actionBoxes.size+2)

            val stakeRecord: Coll[Byte] = stakeTree(stakeState).get(stakeKey, stakeProof).get

            val currentTime: Long = CONTEXT.preHeader.timestamp

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Simple conditions                                                     //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val enoughStaked: Boolean = stakeRecordStake(stakeRecord) >= minStakeAmount

            val keyInOutput: Boolean = tokenExists((OUTPUTS, stakeKey))

            val correctDAOOutput: Boolean = allOf(
                Coll(
                    blake2b256(daoOriginO.propositionBytes) == daoOriginContractHash,
                    daoOriginO.value                 >= daoOrigin.value,
                    daoToken(daoOriginO)             == daoToken(daoOrigin),
                    daoProposalToken(daoOriginO)._1  == daoProposalToken(daoOrigin)._1,
                    daoProposalToken(daoOriginO)._2  == daoProposalToken(daoOrigin)._2 - 1L,
                    daoActionToken(daoOriginO)._1    == daoActionToken(daoOrigin)._1,
                    daoActionToken(daoOriginO)._2    == daoActionToken(daoOrigin)._2 - actionBoxes.size,
                    daoOriginO.tokens.size           == 3,
                )
            )

            val correctProposalOutput: Boolean = allOf(
                Coll(
                    proposalO.value                        >= proposalBox.value,
                    proposalO.value                        >= 5000000L,
                    pIndex(proposalO)                      == proposalId,
                    pProposalToken(proposalO)._1           == daoProposalToken(daoOrigin)._1,
                    pProposalToken(proposalO)._2           == 1L,
                    pPaideiaToken(proposalO)._1            == paideiaTokenId,
                    pPaideiaToken(proposalO)._2            == createProposalFee,
                    proposalO.propositionBytes             == proposalBox.propositionBytes,
                    pEndTime(proposalO)                    >= currentTime + minProposalTime,
                    blake2b256(proposalO.propositionBytes) == proposalContractHash,
                    pVoted(proposalO)                      == 0L,
                    pVotes(proposalO).forall{(p: Long) => p == 0L}
                )
            )

            val correctActionOutputs: Boolean = actionOutputs.indices.forall{
                (i: Int) =>
                allOf(Coll(
                    actionOutputs(i).value            >= actionBoxes(i).value,
                    actionOutputs(i).value            >= 2000000L,
                    aActionToken(actionOutputs(i))._1 == daoActionToken(daoOrigin)._1,
                    aActionToken(actionOutputs(i))._2 == 1L,
                    aProposalIndex(actionOutputs(i))  == proposalId,
                    aProposalOption(actionOutputs(i)) > 0L,
                    blake2b256(actionOutputs(i).propositionBytes) == 
                        actionContractHashes(i)
                ))
            }

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Final contract result                                                 //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            allOf(Coll(
                correctDAOOutput,
                correctProposalOutput,
                correctActionOutputs,
                enoughStaked,
                keyInOutput
            ))
        } else {
            false
        }
    }

    def validUpdateOrRefresh(txType: Byte): Boolean = {
        if (txType == UPDATE) {
            val config = filterByTokenId((CONTEXT.dataInputs, imPaideiaDaoKey))(0)
            updateOrRefresh((imPaideiaContractsDao, config))
        } else {
            false
        }
    }

    sigmaProp(anyOf(Coll(
        validCreateProposalTransaction(transactionType),
        validUpdateOrRefresh(transactionType)
    )))
}