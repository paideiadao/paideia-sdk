{

    /**
     *
     *  DAOOrigin
     *
     *  Holds the dao token verifying it as a genuine Paideia DAO and hands
     *  controls the proposal/action creation process.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaDaoKey: Coll[Byte]              = _PAIDEIA_DAO_KEY
    val imPaideiaContractsDao: Coll[Byte]      = _IM_PAIDEIA_CONTRACTS_DAO
    val imPaideiaContractsProposal: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_PROPOSAL
    val imPaideiaContractsAction: Coll[Byte]   = _IM_PAIDEIA_CONTRACTS_ACTION
    val paideiaTokenId: Coll[Byte]             = _PAIDEIA_TOKENID

    val imPaideiaFeesCreateProposalPaideia: Coll[Byte] =  
        _IM_PAIDEIA_FEES_CREATEPROPOSAL_PAIDEIA

    val imPaideiaDaoMinProposalTime: Coll[Byte] = 
        _IM_PAIDEIA_DAO_MIN_PROPOSAL_TIME

    val maxLong: Long = 9223372036854775807L

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Inputs                                                                //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoOrigin: Box      = SELF
    val createProposal: Box = INPUTS(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Data Inputs                                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfig: Box = CONTEXT.dataInputs(0)
    val config: Box        = CONTEXT.dataInputs(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Outputs                                                               //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoOriginO: Box = OUTPUTS(0)
    val proposalO: Box  = OUTPUTS(1)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Registers                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoKey: Coll[Byte] = daoOrigin.R4[Coll[Byte]].get

    val daoKeyO: Coll[Byte] = daoOriginO.R4[Coll[Byte]].get

    val paideiaConfigTree: AvlTree = paideiaConfig.R4[AvlTree].get

    val configTree: AvlTree = config.R4[AvlTree].get

    val createProposalR5: Coll[Box] = createProposal.R5[Coll[Box]].get
    val proposalBox: Box            = createProposalR5(0)

    val actionBoxes: Coll[Box] = createProposalR5.slice(1,createProposalR5.size)

    val proposalIndex: Long    = proposalO.R4[Coll[Int]].get(0).toLong
    val proposalOR5: Coll[Long] = proposalO.R5[Coll[Long]].get
    val proposalEndTime: Long  = proposalOR5(0)
    
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Context variables                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfigProof: Coll[Byte] = getVar[Coll[Byte]](0).get
    val configProof: Coll[Byte]        = getVar[Coll[Byte]](1).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // DAO Config value extraction                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
        paideiaConfigTree.getMany(
            Coll(
                imPaideiaContractsDao,
                imPaideiaFeesCreateProposalPaideia
            ),
            paideiaConfigProof
        )

    val daoOriginContractHash: Coll[Byte] = paideiaConfigValues(0).get.slice(1,33)
    val createProposalFee: Long = 
        byteArrayToLong(paideiaConfigValues(1).get.slice(1,9))

    val configValues: Coll[Option[Coll[Byte]]] = configTree.getMany(
        Coll(
            imPaideiaDaoMinProposalTime,
            blake2b256(imPaideiaContractsProposal++proposalBox.propositionBytes)
        )++actionBoxes.map{
            (box: Box) =>
            blake2b256(imPaideiaContractsAction++box.propositionBytes)
        },
        configProof
    )

    val minProposalTime: Long = byteArrayToLong(configValues(0).get.slice(1,9))
    val proposalContractHash: Coll[Byte] = configValues(1).get.slice(1,33)
    val actionContractHashes: Coll[Coll[Byte]] = 
        configValues.slice(2,configValues.size).map{
            (cv: Option[Coll[Byte]]) =>
            cv.get.slice(1,33)
        }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val proposalId: Long = maxLong - daoOrigin.tokens(1)._2

    val actionOutputs: Coll[Box] = OUTPUTS.slice(2,actionBoxes.size+2)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Simple conditions                                                     //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val paideiaCorrectConfig: Boolean = 
        paideiaConfig.tokens(0)._1 == paideiaDaoKey

    val correctConfig: Boolean = config.tokens(0)._1 == daoKey

    val currentTime: Long = CONTEXT.preHeader.timestamp

    val correctDAOOutput: Boolean = allOf(
        Coll(
            blake2b256(daoOriginO.propositionBytes) == daoOriginContractHash,
            daoOriginO.value        >= daoOrigin.value,
            daoOriginO.tokens(0)    == daoOrigin.tokens(0),
            daoOriginO.tokens(1)._1 == daoOrigin.tokens(1)._1,
            daoOriginO.tokens(1)._2 == daoOrigin.tokens(1)._2 - 1L,
            daoOriginO.tokens(2)._1 == daoOrigin.tokens(2)._1,
            daoOriginO.tokens(2)._2 == daoOrigin.tokens(2)._2 - actionBoxes.size,
            daoOriginO.tokens.size  == 3,
            daoKeyO                 == daoKey
        )
    )

    val correctProposalOutput: Boolean = allOf(
        Coll(
            proposalO.value >= proposalBox.value,
            proposalIndex == proposalId,
            proposalO.tokens(0)._1 == daoOrigin.tokens(1)._1,
            proposalO.tokens(0)._2 == 1L,
            proposalO.tokens(1)._1 == paideiaTokenId,
            proposalO.tokens(1)._2 == createProposalFee,
            proposalO.propositionBytes == proposalBox.propositionBytes,
            blake2b256(proposalO.propositionBytes) == proposalContractHash,
            proposalEndTime >= currentTime + minProposalTime,
            proposalOR5.slice(1,proposalOR5.size).forall{(p: Long) => p == 0L}
        )
    )

    val correctActionOutputs: Boolean = actionOutputs.indices.forall{
        (i: Int) =>
        allOf(Coll(
            actionOutputs(i).value >= actionBoxes(i).value,
            actionOutputs(i).tokens(0)._1 == daoOrigin.tokens(2)._1,
            actionOutputs(i).tokens(0)._2 == 1L,
            actionOutputs(i).R4[Coll[Long]].get(0) == proposalId,
            actionOutputs(i).R4[Coll[Long]].get(1) >= 0L,
            blake2b256(actionOutputs(i).propositionBytes) == 
                actionContractHashes(i)
        ))
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(allOf(Coll(
        paideiaCorrectConfig,
        correctConfig,
        correctDAOOutput,
        correctProposalOutput,
        correctActionOutputs
    )))
}