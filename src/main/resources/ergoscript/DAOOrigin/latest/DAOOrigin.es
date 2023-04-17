{
    val daoInput = SELF

    val maxLong = 9223372036854775807L

    val paideiaConfigInput = CONTEXT.dataInputs(0)

    val paideiaCorrectConfig = paideiaConfigInput.tokens(0)._1 == _PAIDEIA_DAO_KEY

    val paideiaConfigProof = getVar[Coll[Byte]](0).get

    val paideiaConfigValues = paideiaConfigInput.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_CONTRACTS_DAO,
        _IM_PAIDEIA_FEES_CREATEPROPOSAL_PAIDEIA
    ),paideiaConfigProof)

    val daoOutput = OUTPUTS(0)

    val configInput = CONTEXT.dataInputs(1)

    val correctConfig = configInput.tokens(0)._1 == daoInput.R4[Coll[Byte]].get

    val configProof = getVar[Coll[Byte]](1).get

    val validTransaction = {
        if (daoInput.tokens(1)._2-1 == daoOutput.tokens(1)._2) {
            val createProposalInput = INPUTS(1)

            val actionBoxes = createProposalInput.R5[Coll[Box]].get.slice(1,createProposalInput.R5[Coll[Box]].get.size)
            val proposalBox = createProposalInput.R5[Coll[Box]].get(0)

            val proposalId = maxLong-daoInput.tokens(1)._2

            val configValues = configInput.R4[AvlTree].get.getMany(
                Coll(blake2b256(_IM_PAIDEIA_CONTRACTS_PROPOSAL++proposalBox.propositionBytes))++
                actionBoxes.map{
                    (box: Box) =>
                    blake2b256(_IM_PAIDEIA_CONTRACTS_ACTION++box.propositionBytes)
                }
            ,configProof)

            val correctDAOOutput = allOf(Coll(
                blake2b256(daoOutput.propositionBytes) == paideiaConfigValues(0).get.slice(1,33),
                daoOutput.value >= daoInput.value,
                daoOutput.tokens(0) == daoInput.tokens(0),
                daoOutput.tokens(1)._1 == daoInput.tokens(1)._1,
                daoOutput.tokens(2) == daoInput.tokens(2),
                daoOutput.tokens(3)._1 == daoInput.tokens(3)._1,
                daoOutput.tokens(3)._2 == daoInput.tokens(3)._2 - actionBoxes.size,
                daoOutput.tokens.size == 4
            ))

            val proposalOutput = OUTPUTS(1)

            val correctProposalOutput = allOf(Coll(
                proposalOutput.value >= proposalBox.value,
                proposalOutput.R4[Coll[Int]].get(0).toLong == proposalId,
                proposalOutput.tokens(0)._1 == daoInput.tokens(1)._1,
                proposalOutput.tokens(0)._2 == 1L,
                proposalOutput.tokens(1)._1 == _PAIDEIA_TOKENID,
                proposalOutput.tokens(1)._2 == byteArrayToLong(paideiaConfigValues(1).get.slice(1,9)),
                proposalOutput.propositionBytes == createProposalInput.R5[Coll[Box]].get(0).propositionBytes,
                blake2b256(proposalOutput.propositionBytes) == configValues(0).get.slice(1,33)
            ))

            val actionOutputs = OUTPUTS.slice(2,actionBoxes.size+2)

            val correctActionOutputs = actionOutputs.indices.forall{
                (i: Int) =>
                allOf(Coll(
                    actionOutputs(i).value >= actionBoxes(i).value,
                    actionOutputs(i).tokens(0)._1 == daoInput.tokens(3)._1,
                    actionOutputs(i).tokens(0)._2 == 1L,
                    actionOutputs(i).R4[Coll[Long]].get(0) == proposalId,
                    actionOutputs(i).R4[Coll[Long]].get(1) >= 0L,
                    blake2b256(actionOutputs(i).propositionBytes) == configValues(i+1).get.slice(1,33)
                ))
            }

            allOf(Coll(
                correctDAOOutput,
                correctProposalOutput,
                correctActionOutputs
            ))

        } else {
            false
        }
    }

    sigmaProp(allOf(Coll(
        paideiaCorrectConfig,
        correctConfig,
        validTransaction,
    )))
}