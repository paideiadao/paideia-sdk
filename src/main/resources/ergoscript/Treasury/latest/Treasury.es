{
    /**
     *
     *  Treasury
     *
     *  The DAO treasury. The assets guarded by this contract can only be spend
     *  through passed proposals or to fund the running of the DAO.
     *
     */

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Constants                                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val daoActionTokenId: Coll[Byte]        = _IM_PAIDEIA_DAO_ACTION_TOKENID
    val imPaideiaDaoKey: Coll[Byte]         = _IM_PAIDEIA_DAO_KEY
    val paideiaTokenId: Coll[Byte]          = _IM_PAIDEIA_TOKEN_ID
    val imPaideiaFeeEmitPaideia: Coll[Byte] = _IM_PAIDEIA_FEE_EMIT_PAIDEIA

    val imPaideiaFeeOperatorMaxErg: Coll[Byte] = 
        _IM_PAIDEIA_FEE_OPERATOR_MAX_ERG

    val imPaideiaContractsSplitProfit: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT

    val imPaideiaFeeEmitOperatorPaideia: Coll[Byte] = 
        _IM_PAIDEIA_FEE_EMIT_OPERATOR_PAIDEIA

    val imPaideiaFeeCompoundOperatorPaideia: Coll[Byte] = 
        _IM_PAIDEIA_FEE_COMPOUND_OPERATOR_PAIDEIA

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val validAction: Boolean = INPUTS.exists{
        (box: Box) =>
        if (box.tokens.size > 0) {
            box.tokens(0)._1 == daoActionTokenId
        } else {
            false
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Transaction dependent logic                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val validStakeOp = if (!validAction) {
        /**
        * Relevant for stake transactions only
        * The treasury funds the off chain actions needed for staking
        */

        ///////////////////////////////////////////////////////////////////////
        // Inputs                                                            //
        ///////////////////////////////////////////////////////////////////////

        val stakeState: Box = INPUTS(0)
        val treasury: Box   = SELF

        ///////////////////////////////////////////////////////////////////////
        // Data Inputs                                                       //
        ///////////////////////////////////////////////////////////////////////

        val paideiaConfig: Box = CONTEXT.dataInputs(1)

        ///////////////////////////////////////////////////////////////////////
        // Outputs                                                           //
        ///////////////////////////////////////////////////////////////////////

        val stakeStateO: Box = OUTPUTS(0)

        val treasuryO: Box = OUTPUTS.filter{
            (b: Box) => 
            b.propositionBytes == treasury.propositionBytes
        }(0)

        ///////////////////////////////////////////////////////////////////////
        // Registers                                                         //
        ///////////////////////////////////////////////////////////////////////

        val stakeStateR5: Coll[Long] = stakeState.R5[Coll[Long]].get
        val nextSnapshot: Long       = stakeStateR5(0)
        val totalStaked: Long        = stakeStateR5(1)

        val stakeStateOR5: Coll[Long] = stakeStateO.R5[Coll[Long]].get
        val nextSnapshotO: Long       = stakeStateOR5(0)
        val totalStakedO: Long        = stakeStateOR5(1)
        val stakersO: Long            = stakeStateOR5(2)

        val paideiaConfigTree: AvlTree = paideiaConfig.R4[AvlTree].get

        ///////////////////////////////////////////////////////////////////////
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val paideiaProof: Coll[Byte] = getVar[Coll[Byte]](0).get

        ///////////////////////////////////////////////////////////////////////
        // Intermediate calculations                                         //
        ///////////////////////////////////////////////////////////////////////

        val treasuryInInput: Coll[Box] = INPUTS.filter{
            (b: Box) => 
            b.propositionBytes == treasury.propositionBytes
        }

        val treasuryNerg: Long = treasuryInInput.fold(0L, {
            (z: Long, b: Box) => z + b.value
        })

        def tokensInBoxes(tokenId: Coll[Byte]): Long = 
            treasuryInInput.flatMap{(b: Box) => b.tokens}
                .fold(0L, {
                    (z: Long, token: (Coll[Byte], Long)) => 
                    z + (if (token._1 == tokenId) token._2 else 0L)
                })

        val treasuryPaideia: Long = tokensInBoxes(paideiaTokenId)

        val snapshotTx: Boolean = nextSnapshotO > nextSnapshot

        ///////////////////////////////////////////////////////////////////////
        // Simple conditions                                                 //
        ///////////////////////////////////////////////////////////////////////

        val correctPaideiaConfig: Boolean = 
            paideiaConfig.tokens(0)._1 == imPaideiaDaoKey

        val noMissingTokens: Boolean = treasuryInInput.flatMap{
            (b: Box) => b.tokens
        }.forall{
            (t: (Coll[Byte], Long)) => 
            t._1 == paideiaTokenId || treasuryO.tokens.exists{
                (to: (Coll[Byte], Long)) => 
                to._1 == t._1
            }
        }

        val correctTokens: Boolean = treasuryO.tokens.filter{
            (t: (Coll[Byte], Long)) => 
            t._1 != paideiaTokenId
        }.forall{
            (t: (Coll[Byte], Long)) =>
            t._2 == tokensInBoxes(t._1)
        }

        ///////////////////////////////////////////////////////////////////////
        // Transaction validity                                              //
        ///////////////////////////////////////////////////////////////////////

        if (snapshotTx) {
            val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
                paideiaConfigTree.getMany(Coll(
                    imPaideiaFeeEmitPaideia,
                    imPaideiaFeeEmitOperatorPaideia,
                    imPaideiaContractsSplitProfit,
                    imPaideiaFeeOperatorMaxErg
                ), paideiaProof)

            val baseFee: Long = byteArrayToLong(
                paideiaConfigValues(0).get.slice(1,9)
            )

            val paideiaOperator: Long = byteArrayToLong(
                paideiaConfigValues(1).get.slice(1,9)
            )

            val contractSplitProfitHash: Coll[Byte] = 
                paideiaConfigValues(2).get.slice(1,33)

            val maxErgOperator: Long = byteArrayToLong(
                paideiaConfigValues(3).get.slice(1,9)
            )

            val splitProfitOutput: Box = OUTPUTS.filter{
                (b: Box) => 
                blake2b256(b.propositionBytes) == contractSplitProfitHash
            }(0)

            val paideiaFee: Long = baseFee*stakersO+1L

            val correctErg: Boolean = 
                treasuryO.value >= treasuryNerg - maxErgOperator

            val correctPaideia: Boolean = treasuryO.tokens.fold(0L, {
                    (z: Long, t: (Coll[Byte], Long)) => 
                    z + (if (t._1 == paideiaTokenId) t._2 else 0L)
                }) >= treasuryPaideia - paideiaFee - paideiaOperator
                    
            allOf(Coll(
                correctErg,
                correctPaideia,
                correctTokens,
                noMissingTokens,
                splitProfitOutput.tokens(0)._1 == paideiaTokenId,
                splitProfitOutput.tokens(0)._2 >= paideiaFee
            ))
        } else {
            val paideiaConfigValues = paideiaConfigTree.getMany(Coll(
                imPaideiaFeeCompoundOperatorPaideia,
                imPaideiaFeeOperatorMaxErg
            ), paideiaProof)

            val paideiaOperator: Long = byteArrayToLong(
                paideiaConfigValues(0).get.slice(1,9)
            )

            val maxErgOperator: Long = byteArrayToLong(
                paideiaConfigValues(1).get.slice(1,9)
            )

            val correctErg: Boolean = 
                treasuryO.value >= treasuryNerg - maxErgOperator

            val correctPaideia: Boolean = treasuryO.tokens.fold(0L, {
                (z: Long, t: (Coll[Byte], Long)) => 
                z + (if (t._1 == paideiaTokenId) t._2 else 0L)
            }) >= treasuryPaideia - paideiaOperator
                

            val stakedIncrease: Boolean = totalStakedO > totalStaked

            val govTokensSame: Boolean = 
                stakeStateO.tokens(1)._2 == stakeState.tokens(1)._2

            allOf(Coll(
                correctErg,
                correctPaideia,
                correctTokens,
                noMissingTokens,
                stakedIncrease,
                govTokensSame
            ))
        }
    } else {
        false
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(
        anyOf(
            Coll(
                validAction,
                validStakeOp
            )
        )
    )
}