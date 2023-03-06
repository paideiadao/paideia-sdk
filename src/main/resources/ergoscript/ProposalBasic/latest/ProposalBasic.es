{
    val proposalBasicInput = SELF

    val configInput = CONTEXT.dataInputs(0)

    val correctConfig = configInput.tokens(0)._1 == _IM_PAIDEIA_DAO_KEY

    val configProof = getVar[Coll[Byte]](0).get

    val configValues = configInput.R4[AvlTree].get.getMany(Coll(
        _IM_PAIDEIA_DAO_QUORUM,
        _IM_PAIDEIA_STAKING_STATE_TOKENID
    ),configProof)

    val stakeInput = CONTEXT.dataInputs(1)

    val correctStakeInput = stakeInput.tokens(0)._1 == configValues(1).get.slice(6,38)

    val proposalBasicOutput = OUTPUTS(0)

    val passedInput = proposalBasicInput.R4[Coll[Int]].get(1)
    val passedOutput = proposalBasicOutput.R4[Coll[Int]].get(1)

    val validTransaction = if (passedInput != passedOutput && passedInput == -1) {
        val paideiaConfigInput = CONTEXT.dataInputs(2)

        val paideiaCorrectConfig = paideiaConfigInput.tokens(0)._1 == _PAIDEIA_DAO_KEY

        val paideiaConfigProof = getVar[Coll[Byte]](1).get

        val paideiaConfigValues = paideiaConfigInput.R4[AvlTree].get.getMany(Coll(
            _IM_PAIDEIA_FEES_CREATE_PROPOSAL_PAIDEIA,
            _IM_PAIDEIA_CONTRACTS_TREASURY
        ),paideiaConfigProof)

        val quorumNeeded = byteArrayToLong(configValues(0).get.slice(1,9))

        val winningVote = getVar[(Int,Long)](4).get
        val votes = proposalBasicInput.R5[Coll[Long]].get.slice(2,proposalBasicInput.R5[Coll[Long]].get.size)

        val passed = if (proposalBasicInput.R5[Coll[Long]].get(1) > (stakeInput.R5[Coll[Long]].get(2)*quorumNeeded/1000)) 
                winningVote._1
            else 
                -2

        val correctWinningVote = votes.indices.forall{
                    (i: Int) =>
                    if (i==winningVote._1) votes(i)==winningVote._2
                    else votes(i) <= winningVote._2
         } && votes.size > winningVote._1

        val correctOut = allOf(Coll(
            proposalBasicOutput.propositionBytes == proposalBasicInput.propositionBytes,
            proposalBasicOutput.value >= proposalBasicInput.value - 3000000L,
            proposalBasicOutput.tokens(0) == proposalBasicInput.tokens(0),
            proposalBasicOutput.R4[Coll[Int]].get(0) == proposalBasicInput.R4[Coll[Int]].get(0),
            proposalBasicOutput.R5[Coll[Long]].get == proposalBasicInput.R5[Coll[Long]].get,
            proposalBasicOutput.R6[AvlTree].get == proposalBasicInput.R6[AvlTree].get,
            passedOutput == passed,
            correctWinningVote
        ))

        val treasuryOut = OUTPUTS(1)

        val padFee = byteArrayToLong(paideiaConfigValues(0).get.slice(1,9))
        val padTokens = if (SELF.tokens(1)._2 >= padFee) SELF.tokens(1)._2 else padFee

        val correctTreasuryOut = allOf(Coll(
            blake2b256(treasuryOut.propositionBytes) == paideiaConfigValues(1).get.slice(1,33),
            treasuryOut.value >= 1000000L,
            treasuryOut.tokens(0)._1 == _PAIDEIA_TOKEN_ID,
            treasuryOut.tokens(0)._2 >= padTokens
        ))

        val passedEnd = CONTEXT.preHeader.timestamp > proposalBasicInput.R5[Coll[Long]].get(0)

        allOf(Coll(
            paideiaCorrectConfig,
            correctOut,
            passedEnd,
            correctTreasuryOut
        ))
    } else {
        if (proposalBasicInput.R5[Coll[Long]].get != proposalBasicOutput.R5[Coll[Long]].get) {
            val castVoteInput = INPUTS(2)
            val voteInput = INPUTS(1)

            val voteKey = voteInput.R5[Coll[Byte]].get
            val votes = proposalBasicInput.R6[AvlTree].get

            val currentVoteProof = getVar[Coll[Byte]](1).get
            val currentVote = votes.get(voteKey,currentVoteProof)

            val newVoteProof = getVar[Coll[Byte]](2).get

            val newVotes = if (currentVote.isDefined) {
                votes.update(Coll((voteKey,castVoteInput.R5[Coll[Byte]].get)),newVoteProof).get
            } else {
                votes.insert(Coll((voteKey,castVoteInput.R5[Coll[Byte]].get)),newVoteProof).get
            }

            val newVoteValues = castVoteInput.R5[Coll[Byte]].get.indices.slice(0,castVoteInput.R5[Coll[Byte]].get.size/8).map{
                (i: Int) =>
                byteArrayToLong(castVoteInput.R5[Coll[Byte]].get.slice(i*8,(i+1)*8))
            }

            val newVoteCount = newVoteValues.fold(0L,{(z: Long, v: Long) => z+v})

            val correctVoteValues = if (currentVote.isDefined) {
                val oldVoteValues = currentVote.get.indices.slice(0,currentVote.get.size/8).map{
                    (i: Int) =>
                    byteArrayToLong(currentVote.get.slice(i*8,(i+1)*8))
                }
                val oldVoteCount = oldVoteValues.fold(0L,{(z: Long, v: Long) => z+v})

                val changedVoteValues = oldVoteValues.zip(newVoteValues).map{(kv: (Long,Long)) => kv._2-kv._1}

                allOf(Coll(
                    proposalBasicOutput.R5[Coll[Long]].get(1) == proposalBasicInput.R5[Coll[Long]].get(1) - oldVoteCount + newVoteCount,
                    proposalBasicOutput.R5[Coll[Long]].get.slice(2,proposalBasicOutput.R5[Coll[Long]].get.size) == 
                        proposalBasicInput.R5[Coll[Long]].get.slice(2,proposalBasicInput.R5[Coll[Long]].get.size).zip(changedVoteValues).map{(kv: (Long,Long)) => 
                            kv._1+kv._2
                        }
                ))
            } else {
                allOf(Coll(
                    proposalBasicOutput.R5[Coll[Long]].get(1) == proposalBasicInput.R5[Coll[Long]].get(1) + newVoteCount,
                    proposalBasicOutput.R5[Coll[Long]].get.slice(2,proposalBasicOutput.R5[Coll[Long]].get.size) == 
                        proposalBasicInput.R5[Coll[Long]].get.slice(2,proposalBasicInput.R5[Coll[Long]].get.size).zip(newVoteValues).map{(kv: (Long,Long)) => 
                            kv._1+kv._2
                        }
                ))
            }

            val stakeProof = getVar[Coll[Byte]](3).get

            val currentStakeState = stakeInput.R4[AvlTree].get.get(voteInput.tokens(1)._1, stakeProof).get

            val currentStakeAmount = byteArrayToLong(currentStakeState.slice(0,8))

            allOf(Coll(
                currentStakeAmount >= newVoteCount,
                proposalBasicOutput.propositionBytes == proposalBasicInput.propositionBytes,
                proposalBasicOutput.value >= proposalBasicInput.value,
                proposalBasicOutput.tokens(0) == proposalBasicInput.tokens(0),
                proposalBasicOutput.R4[Coll[Int]].get == proposalBasicInput.R4[Coll[Int]].get,
                proposalBasicOutput.R5[Coll[Long]].get(0) == proposalBasicInput.R5[Coll[Long]].get(0),
                correctVoteValues,
                proposalBasicOutput.R6[AvlTree].get.digest == newVotes.digest
            ))
        } else {
            false
        }
    }

    sigmaProp(allOf(Coll(
        correctConfig,
        correctStakeInput,
        validTransaction
    )))
}