{
    val maxLong = 9223372036854775807L

    val transactionType = getVar[Byte](0).get

    val paideiaConfigBox = CONTEXT.dataInputs(0)
    val paideiaConfig = paideiaConfigBox.R4[AvlTree].get
    val paideiaConfigProof = getVar[Coll[Byte]](1).get

    val correctDataInput = paideiaConfigBox.tokens(0)._1 == _PAIDEIA_DAO_KEY

    val config = SELF.R4[AvlTree].get

    val configProof = getVar[Coll[Byte]](2).get

    val validTransaction = if (transactionType == 0.toByte) {
        
        val paideiaConfigValues = paideiaConfig.getMany(Coll(
            _IM_PAIDEIA_CONTRACTS_PROTODAO,
            _IM_PAIDEIA_CONTRACTS_MINT
        ),paideiaConfigProof)

        val mintAction = getVar[Coll[Byte]](3).get

        val configValues = config.getMany(Coll(
            _IM_PAIDEIA_DAO_NAME
        ),configProof)

        val configInsertProof = getVar[Coll[Byte]](4).get
        
        val configOut = config.insert(Coll(
            (mintAction,Coll(10.toByte,0.toByte,0.toByte,0.toByte,0.toByte,32.toByte) ++ SELF.id)
        ),configInsertProof).get

        val mintOut = OUTPUTS(1)
        val daoName = configValues(0).get.slice(5,configValues(0).get.size)

        val mintInfo = 
            if (mintAction == _IM_PAIDEIA_DAO_PROPOSAL_TOKENID) {
                (daoName++_PROPOSAL,maxLong)
            } else {
                if (mintAction == _IM_PAIDEIA_DAO_ACTION_TOKENID) {
                    (daoName++_ACTION,maxLong)
                } else {
                    if (mintAction == _IM_PAIDEIA_STAKING_STATE_TOKENID) {
                        (daoName++_STAKE_STATE,1L)
                    } else {
                        (daoName,-1L)
                    }
                }
            }
        

        val validMint = allOf(Coll(
            blake2b256(mintOut.propositionBytes) == paideiaConfigValues(1).get.slice(1,33),
            mintOut.tokens(0)._1 == SELF.id,
            mintOut.tokens(0)._2 == mintInfo._2,
            mintOut.R4[Coll[Byte]].get == mintInfo._1,
            mintOut.R5[Coll[Byte]].get == mintOut.R4[Coll[Byte]].get,
            mintOut.R6[Coll[Byte]].get == Coll(48.toByte)
        ))

        val protoDAOOut = OUTPUTS(0)

        val validProtoDAOOut = allOf(Coll(
            blake2b256(protoDAOOut.propositionBytes) == paideiaConfigValues(0).get.slice(1,33),
            protoDAOOut.tokens == SELF.tokens,
            protoDAOOut.R4[AvlTree].get.digest == configOut.digest
        ))

        allOf(Coll(
            correctDataInput,
            validMint,
            validProtoDAOOut
        ))
    } else {
        if (transactionType == 1.toByte) {
            val paideiaConfigValues = paideiaConfig.getMany(Coll(
                _IM_PAIDEIA_CONTRACTS_DAO,
                _IM_PAIDEIA_DEFAULT_CONFIG,
                _IM_PAIDEIA_DEFAULT_CONFIG_SIGNATURE,
                _IM_PAIDEIA_DEFAULT_TREASURY,
                _IM_PAIDEIA_DEFAULT_TREASURY_SIGNATURE
            ),paideiaConfigProof)
            
            val configValues = config.getMany(Coll(
                _IM_PAIDEIA_DAO_PROPOSAL_TOKENID,
                _IM_PAIDEIA_DAO_ACTION_TOKENID,
                _IM_PAIDEIA_DAO_KEY
            ),configProof)

            val daoOutput = OUTPUTS(0)
            val configOutput = OUTPUTS(1)

            val correctDAOOutput = allOf(Coll(
                blake2b256(daoOutput.propositionBytes) == paideiaConfigValues(0).get.slice(1,33),
                daoOutput.value >= 1000000L,
                daoOutput.tokens(0) == SELF.tokens(0),
                daoOutput.tokens(1)._1 == configValues(0).get.slice(6,38),
                daoOutput.tokens(1)._2 == maxLong,
                daoOutput.tokens(2)._1 == configValues(1).get.slice(6,38),
                daoOutput.tokens(2)._2 == maxLong,
                daoOutput.tokens.size == 3,
                daoOutput.R4[Coll[Byte]].get == configValues(2).get.slice(6,38)
            ))

            val insertProof = getVar[Coll[Byte]](3).get

            val insertValues = getVar[Coll[Coll[Byte]]](4).get

            val treasuryContractSignature = insertValues(0)
            val configContractSignature = insertValues(1)

            val finalConfig = config.insert(Coll(
                (_IM_PAIDEIA_CONTRACTS_TREASURY,treasuryContractSignature),
                (_IM_PAIDEIA_CONTRACTS_CONFIG,configContractSignature)
            ),insertProof)

            val correctConfigOutput = allOf(Coll(
                blake2b256(configOutput.propositionBytes) == configContractSignature.slice(1,33),
                configOutput.value >= 1000000L,
                configOutput.tokens(0)._1 == configValues(2).get.slice(6,38),
                configOutput.tokens(0)._2 == 1L,
                configOutput.tokens.size == 1,
                configOutput.R4[AvlTree].get.digest == finalConfig.get.digest
            ))

            val correctConfigContract = blake2b256(substConstants(paideiaConfigValues(1).get.slice(6,paideiaConfigValues(1).get.size),Coll(7),Coll(configValues(1).get.slice(6,38))))
            val correctTreasuryContract = blake2b256(substConstants(paideiaConfigValues(3).get.slice(6,paideiaConfigValues(3).get.size),Coll(2),Coll(configValues(1).get.slice(6,38))))

            val correctContracts = allOf(Coll(
                paideiaConfigValues(2).get.patch(1,correctConfigContract,32) == configContractSignature,
                paideiaConfigValues(4).get.patch(1,correctTreasuryContract,32) == treasuryContractSignature
            ))

            allOf(Coll(
                correctContracts,
                correctDataInput,
                correctDAOOutput,
                correctConfigOutput
            ))
        } else {
            false
        }
    }

    sigmaProp(validTransaction)
}