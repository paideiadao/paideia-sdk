{
    val daoOriginInput = INPUTS(0)

    val correctDaoOrigin = daoOriginInput.R4[Coll[Byte]].get == _IM_PAIDEIA_DAO_KEY

    sigmaProp(correctDaoOrigin)
}