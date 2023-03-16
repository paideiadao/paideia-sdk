{
    // Get config data input and check if it has the correct value
    val configInput = CONTEXT.dataInputs(0)
    val correctConfig = configInput.tokens(0)._1 == _IM_PAIDEIA_DAO_KEY

    // Get configuration values from AVL tree using proof
    val configProof = getVar[Coll[Byte]](0).get
    val configValues = configInput.R4[AvlTree].get.getMany(
        Coll(
            _IM_PAIDEIA_CONTRACTS_TREASURY
        ),
        configProof
    )

    // Get proposal input and check correctness of the inputs
    val proposalInput = CONTEXT.dataInputs(1)
    val correctProposal = allOf(Coll(
        proposalInput.tokens(0)._1 == _IM_PAIDEIA_DAO_PROPOSAL_TOKENID,
        proposalInput.R4[Coll[Int]].get(0).toLong == SELF.R4[Coll[Long]].get(0),
        proposalInput.R4[Coll[Int]].get(1).toLong == SELF.R4[Coll[Long]].get(1)
    ))

    // Check if activation time has passed
    val activationTimePassed = CONTEXT.preHeader.timestamp >= SELF.R4[Coll[Long]].get(3)

    // Check if the current action is being repeated
    val repeatedAction = SELF.R4[Coll[Long]].get(2) > 0L

    // Based on the repeatedAction flag, either repeat or burn the action
    val repeatOrBurn = if (repeatedAction) {
        // Create output box for repeated action and check its correctness
        val repeatOutput = OUTPUTS(SELF.R5[Coll[Box]].get.size)
        allOf(Coll(
            repeatOutput.value >= SELF.value,
            repeatOutput.tokens == SELF.tokens,
            repeatOutput.R4[Coll[Long]].get(0) == SELF.R4[Coll[Long]].get(0),
            repeatOutput.R4[Coll[Long]].get(1) == SELF.R4[Coll[Long]].get(1),
            repeatOutput.R4[Coll[Long]].get(2) == SELF.R4[Coll[Long]].get(2) - 1L,
            repeatOutput.R4[Coll[Long]].get(3) == SELF.R4[Coll[Long]].get(3) + SELF.R4[Coll[Long]].get(4),
            repeatOutput.R4[Coll[Long]].get(4) == SELF.R4[Coll[Long]].get(4),
            repeatOutput.R5[Coll[Box]].get == SELF.R5[Coll[Box]].get,
            repeatOutput.propositionBytes == SELF.propositionBytes
        ))
    } else {
        // Check that no tokens are burned unnecessarily
        !(OUTPUTS.exists{
            (b: Box) =>
            b.tokens.exists{
                (token: (Coll[Byte],Long)) =>
                token._1 == SELF.tokens(0)._1
            }
        })
    }

    // Check if the outputs are correct
    val correctOutput = SELF.R5[Coll[Box]].get.zip(OUTPUTS.slice(0,SELF.R5[Coll[Box]].get.size-1)).forall{
        (boxes: (Box,Box)) =>
        boxes._1.bytesWithoutRef == boxes._2.bytesWithoutRef
    }

    // Check if change box exists and its correctness
    val changeBox = blake2b256(OUTPUTS(OUTPUTS.size-1).propositionBytes) == configValues(0).get.slice(1,33)

    // Check if the number of outputs is correct
    val correctOutputNumber = OUTPUTS.size == SELF.R5[Coll[Box]].get.size +
        (if (repeatedAction) 1 else 0) +
        (if (changeBox) 2 else 1)

    // Helper function to count tokens in a list of boxes
    def countTokens(boxes: Coll[Box]): Long = 
        boxes.flatMap{(b: Box) => b.tokens}.fold(0L, {
            (z: Long, token: (Coll[Byte], Long)) =>
            z + token._2
        })

    // Check that no unnecessary tokens are burned
    val tokensInInput = countTokens(INPUTS)
    val tokensInOutput = countTokens(OUTPUTS)
    val noExtraBurn = tokensInInput == tokensInOutput + (if (repeatedAction) 0L else 1L)

    // Combine all correctness checks into one sigma proposition
    sigmaProp(allOf(Coll(
        correctConfig,
        correctProposal,
        activationTimePassed,
        repeatOrBurn,
        correctOutput,
        correctOutputNumber,
        noExtraBurn
    )))
}
