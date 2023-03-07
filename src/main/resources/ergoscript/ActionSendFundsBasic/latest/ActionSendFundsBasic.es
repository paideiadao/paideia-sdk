{
    val configInput = CONTEXT.dataInputs(0)

    val correctConfig = configInput.tokens(0)._1 == _IM_PAIDEIA_DAO_KEY

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = configInput.R4[AvlTree].get.getMany(
        Coll(
            _IM_PAIDEIA_CONTRACTS_TREASURY
        ),
        configProof
    )

    val proposalInput = CONTEXT.dataInputs(1)

    val correctProposal = allOf(Coll(
        proposalInput.tokens(0)._1 == _IM_PAIDEIA_DAO_PROPOSAL_TOKENID,
        proposalInput.R4[Coll[Int]].get(0).toLong == SELF.R4[Coll[Long]].get(0),
        proposalInput.R4[Coll[Int]].get(1).toLong == SELF.R4[Coll[Long]].get(1)
    ))

    val activationTimePassed = CONTEXT.preHeader.timestamp >= SELF.R4[Coll[Long]].get(3)

    val repeatedAction = SELF.R4[Coll[Long]].get(2) > 0L

    val repeatOrBurn = if (repeatedAction) {
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
        !(OUTPUTS.exists{
            (b: Box) =>
            b.tokens.exists{
                (token: (Coll[Byte],Long)) =>
                token._1 == SELF.tokens(0)._1
            }
        })
    }

    val correctOutput = SELF.R5[Coll[Box]].get.zip(OUTPUTS.slice(0,SELF.R5[Coll[Box]].get.size-1)).forall{
        (boxes: (Box,Box)) =>
        boxes._1.bytesWithoutRef == boxes._2.bytesWithoutRef
    }

    val changeBox = blake2b256(OUTPUTS(OUTPUTS.size-1).propositionBytes) == configValues(0).get.slice(1,33)

    val correctOutputNumber = OUTPUTS.size == SELF.R5[Coll[Box]].get.size +
        (if (repeatedAction) 1 else 0) +
        (if (changeBox) 2 else 1)

    def countTokens(boxes: Coll[Box]): Long = 
        boxes.flatMap{(b: Box) => b.tokens}.fold(0L, {
            (z: Long, token: (Coll[Byte], Long)) =>
            z + token._2
        })

    val tokensInInput = countTokens(INPUTS)
    val tokensInOutput = countTokens(OUTPUTS)

    val noExtraBurn = tokensInInput == tokensInOutput + (if (repeatedAction) 0L else 1L)

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