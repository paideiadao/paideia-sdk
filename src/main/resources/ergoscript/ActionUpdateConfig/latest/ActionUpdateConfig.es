{
    val configInput = INPUTS(0)
    val configOutput = OUTPUTS(0)

    val correctConfig = configInput.tokens(0)._1 == _IM_PAIDEIA_DAO_KEY

    val configProof = getVar[Coll[Byte]](0).get

    val proposalInput = CONTEXT.dataInputs(0)

    val correctProposal = allOf(Coll(
        proposalInput.tokens(0)._1 == _IM_PAIDEIA_DAO_PROPOSAL_TOKENID,
        proposalInput.R4[Coll[Int]].get(0).toLong == SELF.R4[Coll[Long]].get(0),
        proposalInput.R4[Coll[Int]].get(1).toLong == SELF.R4[Coll[Long]].get(1)
    ))

    val activationTimePassed = CONTEXT.preHeader.timestamp >= SELF.R4[Coll[Long]].get(3)

    val burnActionToken = !(OUTPUTS.exists{
            (b: Box) =>
            b.tokens.exists{
                (token: (Coll[Byte],Long)) =>
                token._1 == SELF.tokens(0)._1
            }
        })

    val correctOutputNumber = OUTPUTS.size == 2

    def countTokens(boxes: Coll[Box]): Long = 
        boxes.flatMap{(b: Box) => b.tokens}.fold(0L, {
            (z: Long, token: (Coll[Byte], Long)) =>
            z + token._2
        })

    val tokensInInput = countTokens(INPUTS)
    val tokensInOutput = countTokens(OUTPUTS)

    val noExtraBurn = tokensInInput == tokensInOutput + 1L

    val originalConfig = configInput.R4[AvlTree].get

    val deleteActions = SELF.R5[Coll[Coll[Byte]]].get
    val deleteProof = getVar[Coll[Byte]](1).get
    val updateActions = SELF.R6[Coll[(Coll[Byte], Coll[Byte])]].get
    val updateProof = getVar[Coll[Byte]](2).get
    val insertActions = SELF.R7[Coll[(Coll[Byte], Coll[Byte])]].get
    val insertProof = getVar[Coll[Byte]](3).get

    val configAfterDelete = if (deleteActions.size > 0) originalConfig.remove(deleteActions, deleteProof).get else originalConfig
    val configAfterUpdate = if (updateActions.size > 0) configAfterDelete.update(updateActions, updateProof).get else configAfterDelete
    val configAfterInsert = if (insertActions.size > 0) configAfterUpdate.insert(insertActions, insertProof).get else configAfterUpdate

    val configValues = configOutput.R4[AvlTree].get.getMany(
        Coll(
            _IM_PAIDEIA_CONTRACTS_CONFIG
        ),
        configProof
    )

    val correctConfigOutput = allOf(Coll(
        blake2b256(configOutput.propositionBytes) == configValues(0).get.slice(1,33),
        configOutput.tokens == configInput.tokens,
        configOutput.value >= configInput.value,
        configOutput.R4[AvlTree].get.digest == configAfterInsert.digest
    ))

    sigmaProp(allOf(Coll(
        correctConfig,
        correctProposal,
        activationTimePassed,
        correctConfigOutput,
        correctOutputNumber,
        noExtraBurn
    )))

}