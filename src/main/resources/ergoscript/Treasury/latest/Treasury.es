/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 * 
 * @return
 */
@contract def treasury(daoActionTokenId: Coll[Byte], imPaideiaDaoKey: Coll[Byte], paideiaTokenId: Coll[Byte]) = {
    #import lib/tokensInBoxes/1.0.0/tokensInBoxes.es;
    #import lib/config/1.0.0/config.es;
    #import lib/stakeState/1.0.0/stakeState.es;

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

    val imPaideiaFeeEmitPaideia: Coll[Byte] = _IM_PAIDEIA_FEE_EMIT_PAIDEIA

    val imPaideiaFeeOperatorMaxErg: Coll[Byte] = 
        _IM_PAIDEIA_FEE_OPERATOR_MAX_ERG

    val imPaideiaContractsSplitProfit: Coll[Byte] = 
        _IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT

    val imPaideiaFeeEmitOperatorPaideia: Coll[Byte] = 
        _IM_PAIDEIA_FEE_EMIT_OPERATOR_PAIDEIA

    val imPaideiaFeeCompoundOperatorPaideia: Coll[Byte] = 
        _IM_PAIDEIA_FEE_COMPOUND_OPERATOR_PAIDEIA

    val imPaideiaContractsStakingCompound: Coll[Byte] =
        _IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND

    val imPaideiaContractsStakingSnapshot: Coll[Byte] =
        _IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT

    val imPaideiaStakingEmission: Coll[Byte] =
        _IM_PAIDEIA_STAKING_EMISSION

    val imPaideiaGovernanceTokenId: Coll[Byte] =
        _IM_PAIDEIA_DAO_GOVERNANCE_TOKEN_ID

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

        val config: Box        = CONTEXT.dataInputs(0)
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
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val paideiaProof: Coll[Byte] = getVar[Coll[Byte]](0).get
        val configProof: Coll[Byte] = getVar[Coll[Byte]](1).get

        ///////////////////////////////////////////////////////////////////////
        // DAO Config                                                        //
        ///////////////////////////////////////////////////////////////////////

        val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(Coll(
            imPaideiaContractsStakingCompound,
            imPaideiaContractsStakingSnapshot,
            imPaideiaStakingEmission,
            imPaideiaGovernanceTokenId
        ), configProof)

        val compoundContractHash: Coll[Byte] = bytearrayToContractHash(configValues(0))
        val snapshotContractHash: Coll[Byte] = bytearrayToContractHash(configValues(1))
        val stakingEmission: Long = byteArrayToLong(configValues(2).get.slice(1,9))
        val daoTokenId: Coll[Byte] = bytearrayToTokenId(configValues(3))

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

        val treasuryPaideia: Long = tokensInBoxes((treasuryInInput, paideiaTokenId))

        val treasuryDao: Long = tokensInBoxes((treasuryInInput, daoTokenId))

        val snapshotTx: Boolean = nextEmission(stakeStateO) > nextEmission(stakeState)

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

        val correctOtherTokens: Boolean = treasuryO.tokens.filter{
            (t: (Coll[Byte], Long)) => 
            t._1 != paideiaTokenId && t._1 != daoTokenId
        }.forall{
            (t: (Coll[Byte], Long)) =>
            t._2 >= tokensInBoxes((treasuryInInput, t._1))
        }

        val treasuryPaideiaO: Long = tokensInBoxes((Coll(treasuryO), paideiaTokenId))

        val treasuryDaoO: Long = tokensInBoxes((Coll(treasuryO), daoTokenId))

        ///////////////////////////////////////////////////////////////////////
        // Transaction validity                                              //
        ///////////////////////////////////////////////////////////////////////

        if (snapshotTx) {
            val paideiaConfigValues: Coll[Option[Coll[Byte]]] = 
                configTree(paideiaConfig).getMany(Coll(
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

            val paideiaFee: Long = baseFee*stakers(stakeStateO)+1L

            val snapshotContractPresent: Boolean = 
                blake2b256(INPUTS(1).propositionBytes) == snapshotContractHash

            val correctErg: Boolean = 
                treasuryO.value >= treasuryNerg - maxErgOperator

            val paideiaSpent: Long = paideiaFee + paideiaOperator + (
                if (paideiaTokenId == daoTokenId) 
                    stakingEmission 
                else 
                    0L
                )

            val correctPaideiaLeft: Boolean = treasuryPaideiaO >= 
                treasuryPaideia - paideiaSpent

            val correctDaoLeft: Boolean = 
                if (paideiaTokenId == daoTokenId) 
                    true 
                else 
                    treasuryDaoO >= (treasuryDao - stakingEmission)
                    
            allOf(Coll(
                correctErg,
                correctPaideiaLeft,
                correctDaoLeft,
                correctOtherTokens,
                noMissingTokens,
                splitProfitOutput.tokens(0)._1 == paideiaTokenId,
                splitProfitOutput.tokens(0)._2 >= paideiaFee,
                snapshotContractPresent
            ))
        } else {
            
            
            val paideiaConfigValues = configTree(paideiaConfig).getMany(Coll(
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

            val correctPaideiaLeft: Boolean = treasuryPaideiaO >= 
                treasuryPaideia - paideiaOperator
                
            val correctDaoLeft: Boolean = if (paideiaTokenId == daoTokenId) 
                    true
                else
                    treasuryDaoO >= treasuryDao

            val compoundContractPresent: Boolean = 
                blake2b256(INPUTS(1).propositionBytes) == compoundContractHash

            val govTokensSame: Boolean = 
                stakeStateO.tokens(1)._2 == stakeState.tokens(1)._2

            allOf(Coll(
                correctErg,
                correctPaideiaLeft,
                correctDaoLeft,
                correctOtherTokens,
                noMissingTokens,
                compoundContractPresent,
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