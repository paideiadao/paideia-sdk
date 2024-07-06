/**
 * Holds the dao token verifying it as a genuine Paideia DAO and hands
 * controls the proposal/action creation process.
 *
 * @param paideiaDaoKey Token ID of the paideia dao key
 * @param paideiaTokenId Token ID of the paideia token
 *
 * @return
 */
@contract def daoOrigin(paideiaDaoKey: Coll[Byte], paideiaTokenId: Coll[Byte]) = {
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
    val imPaideiaStakingStateTokenId: Coll[Byte] = _IM_PAIDEIA_STAKING_STATE_TOKENID

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
            val config: Box        = filterByTokenId((CONTEXT.dataInputs, daoOriginKey(daoOrigin)))(0)
            val stakeState: Box    = CONTEXT.dataInputs(2)

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
            val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](1).get
            val configProof: Coll[Byte]        = getVar[Coll[Byte]](2).get
            val proposalBox: Box               = getVar[Box](3).get
            val actionBoxes: Coll[Box]         = getVar[Coll[Box]](4).get
            val stakeKey: Coll[Byte]           = getVar[Coll[Byte]](5).get
            val stakeProof: Coll[Byte]         = getVar[Coll[Byte]](6).get

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // DAO Config value extraction                                           //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
                configTree(paideiaConfig).getMany(
                    Coll(
                        imPaideiaContractsDao,
                        imPaideiaFeesCreateProposalPaideia
                    ),
                    paideiaConfigProof
                )

            val daoOriginContractHash: Coll[Byte] = bytearrayToContractHash(paideiaConfigValues(0))
            val createProposalFee: Long = bytearrayToLongClamped((paideiaConfigValues(1),(0L,(100000000000L, 1000L))))

            val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
                Coll(
                    imPaideiaDaoMinProposalTime,
                    imPaideiaDaoMinStakeProposal,
                    imPaideiaStakingStateTokenId,
                    blake2b256(imPaideiaContractsProposal++proposalBox.propositionBytes)
                )++actionBoxes.map{
                    (box: Box) =>
                    blake2b256(imPaideiaContractsAction++box.propositionBytes)
                },
                configProof
            )

            //Min 12 hours, max 1 month, default 24 hours
            val minProposalTime: Long = bytearrayToLongClamped((configValues(0),(43200000L,(2626560000L,86400000L))))
            //Minimum amount staked to be able to create a proposal. Should not be higher than total staked amount to avoid locking the DAO
            val minStakeAmount: Long = bytearrayToLongClamped((configValues(1),(0L, (totalStaked(stakeState)/2L,0L))))
            val stakeStateTokenId: Coll[Byte] = bytearrayToTokenId(configValues(2))
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

            val proposalId: Long = maxLong - daoOrigin.tokens(1)._2

            val actionOutputs: Coll[Box] = OUTPUTS.slice(2,actionBoxes.size+2)

            val stakeRecord: Coll[Byte] = stakeTree(stakeState).get(stakeKey, stakeProof).get

            val currentTime: Long = CONTEXT.preHeader.timestamp

            ///////////////////////////////////////////////////////////////////////////
            //                                                                       //
            // Simple conditions                                                     //
            //                                                                       //
            ///////////////////////////////////////////////////////////////////////////

            val paideiaCorrectConfig: Boolean = 
                configDaoKey(paideiaConfig) == paideiaDaoKey

            val correctConfig: Boolean = configDaoKey(config) == daoOriginKey(daoOrigin)

            val correctStakeState: Boolean = stakeStateNFT(stakeState) == stakeStateTokenId

            val enoughStaked: Boolean = stakeRecordStake(stakeRecord) >= minStakeAmount

            val keyInOutput: Boolean = tokenExists((OUTPUTS, stakeKey))

            val correctDAOOutput: Boolean = allOf(
                Coll(
                    blake2b256(daoOriginO.propositionBytes) == daoOriginContractHash,
                    daoOriginO.value         >= daoOrigin.value,
                    daoOriginO.tokens(0)     == daoOrigin.tokens(0),
                    daoOriginO.tokens(1)._1  == daoOrigin.tokens(1)._1,
                    daoOriginO.tokens(1)._2  == daoOrigin.tokens(1)._2 - 1L,
                    daoOriginO.tokens(2)._1  == daoOrigin.tokens(2)._1,
                    daoOriginO.tokens(2)._2  == daoOrigin.tokens(2)._2 - actionBoxes.size,
                    daoOriginO.tokens.size   == 3,
                    daoOriginKey(daoOriginO) == daoOriginKey(daoOrigin)
                )
            )

            val correctProposalOutput: Boolean = allOf(
                Coll(
                    proposalO.value                        >= proposalBox.value,
                    pIndex(proposalO)                      == proposalId,
                    proposalO.tokens(0)._1                 == daoOrigin.tokens(1)._1,
                    proposalO.tokens(0)._2                 == 1L,
                    proposalO.tokens(1)._1                 == paideiaTokenId,
                    proposalO.tokens(1)._2                 == createProposalFee,
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
                    actionOutputs(i).tokens(0)._1     == daoOrigin.tokens(2)._1,
                    actionOutputs(i).tokens(0)._2     == 1L,
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
                paideiaCorrectConfig,
                correctConfig,
                correctDAOOutput,
                correctProposalOutput,
                correctActionOutputs,
                correctStakeState,
                enoughStaked,
                keyInOutput
            ))
        } else {
            false
        }
    }

    def validUpdateOrRefresh(txType: Byte): Boolean = {
        if (txType == UPDATE) {
            updateOrRefresh((imPaideiaContractsDao, CONTEXT.dataInputs(0)))
        } else {
            false
        }
    }

    sigmaProp(anyOf(Coll(
        validCreateProposalTransaction(transactionType),
        validUpdateOrRefresh(transactionType)
    )))
}