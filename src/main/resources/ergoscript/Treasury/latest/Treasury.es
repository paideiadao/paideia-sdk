/** This is my contracts description.
 * Here is another line describing what it does in more detail.
 * 
 * @return
 */
@contract def treasury(daoKeyId: Coll[Byte], paideiaDaoKey: Coll[Byte], paideiaTokenId: Coll[Byte], daoActionTokenIdAndStakeStateTokenId: Coll[Byte]) = {
    #import lib/config/1.0.0/config.es;
    #import lib/stakeState/1.0.0/stakeState.es;
    #import lib/txTypes/1.0.0/txTypes.es;
    #import lib/box/1.0.0/box.es;

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

    val imPaideiaFeeEmitPaideia: Coll[Byte]  = _IM_PAIDEIA_FEE_EMIT_PAIDEIA
    val imPaideiaContractsAction: Coll[Byte] = _IM_PAIDEIA_CONTRACTS_ACTION

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

    val daoActionTokenId: Coll[Byte] = daoActionTokenIdAndStakeStateTokenId.slice(0,32)
    val stakeStateTokenId: Coll[Byte] = daoActionTokenIdAndStakeStateTokenId.slice(32,64)

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Transaction Type                                                      //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    val transactionType: Byte = getVar[Byte](0).get

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Intermediate calculations                                             //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    def validAction(txType: Byte): Boolean = 
        if (txType == TREASURY_SPEND) {
            val action: Box = filterByTokenId((INPUTS, daoActionTokenId))(0)

            val configProof: Coll[Byte] = getVar[Coll[Byte]](1).get

            val config: Box = filterByTokenId((CONTEXT.dataInputs, daoKeyId))(0)

            val configValues: Coll[Option[Coll[Byte]]] = configTree(config).getMany(
                Coll(
                    blake2b256(imPaideiaContractsAction++action.propositionBytes)
                ),
                configProof
            )

            configValues(0).isDefined
        } else 
            false

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Transaction dependent logic                                           //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    def validStakeTransaction(txType: Byte): Boolean = {
        if (txType == SNAPSHOT || txType == COMPOUND) {
        /**
        * Relevant for stake transactions only
        * The treasury funds the off chain actions needed for staking
        */

        ///////////////////////////////////////////////////////////////////////
        // Inputs                                                            //
        ///////////////////////////////////////////////////////////////////////

        val stakeState: Box = filterByTokenId((INPUTS, stakeStateTokenId))(0)
        val treasury: Box   = SELF

        ///////////////////////////////////////////////////////////////////////
        // Data Inputs                                                       //
        ///////////////////////////////////////////////////////////////////////

        val config: Box        = filterByTokenId((CONTEXT.dataInputs, daoKeyId))(0)
        val paideiaConfig: Box = filterByTokenId((CONTEXT.dataInputs, paideiaDaoKey))(0)

        ///////////////////////////////////////////////////////////////////////
        // Outputs                                                           //
        ///////////////////////////////////////////////////////////////////////

        val stakeStateO: Box = filterByTokenId((OUTPUTS, stakeStateTokenId))(0)

        val treasuryO: Box = filterByHash((OUTPUTS, blake2b256(treasury.propositionBytes)))(0)

        ///////////////////////////////////////////////////////////////////////
        // Context variables                                                 //
        ///////////////////////////////////////////////////////////////////////

        val paideiaProof: Coll[Byte] = getVar[Coll[Byte]](1).get
        val configProof: Coll[Byte]  = getVar[Coll[Byte]](2).get

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

        val treasuryInInput: Coll[Box] = filterByHash((INPUTS, blake2b256(treasury.propositionBytes)))

        val treasuryNerg: Long = ergInBoxes(treasuryInInput)

        val treasuryPaideia: Long = tokensInBoxes((treasuryInInput, paideiaTokenId))

        val treasuryDao: Long = tokensInBoxes((treasuryInInput, daoTokenId))

        ///////////////////////////////////////////////////////////////////////
        // Simple conditions                                                 //
        ///////////////////////////////////////////////////////////////////////

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

        def validSnapshotTransaction(stakeTxType: Byte): Boolean = {
            if (stakeTxType == SNAPSHOT) {
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
                tokensInBoxes((Coll(splitProfitOutput), paideiaTokenId)) >= paideiaFee,
                snapshotContractPresent
            ))
            } else {
                false
            }
        } 
        
        def validCompoundTransaction(stakeTxType: Byte): Boolean = {
            if (stakeTxType == COMPOUND) {
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
                govToken(stakeStateO)._2 == govToken(stakeState)._2

            allOf(Coll(
                correctErg,
                correctPaideiaLeft,
                correctDaoLeft,
                correctOtherTokens,
                noMissingTokens,
                compoundContractPresent,
                govTokensSame
            ))
            } else {
                false
            }
        }

        anyOf(Coll(
            validSnapshotTransaction(transactionType),
            validCompoundTransaction(transactionType)
        ))
        } else {
            false
        }
    }

    def validConsolidateTransaction(txType: Byte): Boolean = {
        if (txType == CONSOLIDATE) {

            ///////////////////////////////////////////////////////////////
            // INPUTS                                                    //
            ///////////////////////////////////////////////////////////////

            val treasuryInputs: Coll[Box] = filterByHash((INPUTS, blake2b256(SELF.propositionBytes)))

            ///////////////////////////////////////////////////////////////
            // OUTPUTS                                                   //
            ///////////////////////////////////////////////////////////////

            val treasuryOutputs: Coll[Box] = filterByHash((OUTPUTS, blake2b256(SELF.propositionBytes)))

            ///////////////////////////////////////////////////////////////
            // Intermediate Calculations                                 //
            ///////////////////////////////////////////////////////////////

            val ergDifference: Long = ergInBoxes(treasuryInputs) - ergInBoxes(treasuryOutputs)

            ///////////////////////////////////////////////////////////////
            // Simple Conditions                                         //
            ///////////////////////////////////////////////////////////////

            val enoughInputs: Boolean = treasuryInputs.size >= 5

            val onlyOneOutputs: Boolean = treasuryOutputs.size == 1

            val tokensPreserved: Boolean = treasuryOutputs(0).tokens.forall{
                (token: (Coll[Byte], Long)) => 
                tokensInBoxes((treasuryInputs, token._1)) == token._2
            }

            val enoughErgPreserved: Boolean = ergDifference <= 2000000L

            val usefulConsolidation: Boolean = ergInBoxes(treasuryInputs) >= 2000000L

            ///////////////////////////////////////////////////////////////
            // Tx Validity                                               //
            ///////////////////////////////////////////////////////////////

            allOf(Coll(
                enoughInputs,
                onlyOneOutputs,
                tokensPreserved,
                enoughErgPreserved,
                usefulConsolidation
            ))
        } else {
            false
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    // Final contract result                                                 //
    //                                                                       //
    ///////////////////////////////////////////////////////////////////////////

    sigmaProp(
        anyOf(
            Coll(
                validStakeTransaction(transactionType),
                validAction(transactionType),
                validConsolidateTransaction(transactionType),
            )
        )
    )
}