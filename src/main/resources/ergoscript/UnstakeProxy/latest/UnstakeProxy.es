{
    // val configTokenId = _IM_PAIDEIA_DAO_KEY 
    // val config = CONTEXT.dataInputs(0)
    // val correctConfigTokenId = config.tokens(0)._1 == configTokenId

    // val configProof = getVar[Coll[Byte]](0).get

    // val configValues = config.R4[AvlTree].get.getMany(Coll(
    //     _IM_PAIDEIA_STAKING_STATE_TOKENID
    // ),configProof)

    // val plasmaStakingInput = INPUTS(0)
    // val correctPlasmaStakingInput = INPUTS(0).tokens(0)._1 == configValues(0).get.slice(6,38)
    // val plasmaStakingOutput = OUTPUTS(0)
    // val userOutput = OUTPUTS(1)

    // val stakeState = plasmaStakingInput.R4[AvlTree].get

    sigmaProp(true && CONTEXT.preHeader.timestamp > 3)
}