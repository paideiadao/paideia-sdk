package im.paideia.staking

import im.paideia.DAO
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import im.paideia.Paideia

object StakingTest {
    def testDAO: DAO = {
        val daoKey = Util.randomKey
        val config = DAOConfig()
        val stakeStateTokenId = Util.randomKey
        val daoTokenId = Util.randomKey

        config.set(ConfKeys.im_paideia_dao_name,"Test DAO")
        config.set(ConfKeys.im_paideia_staking_state_tokenid,ErgoId.create(stakeStateTokenId).getBytes())
        config.set(ConfKeys.im_paideia_staking_profit_tokenids,Array[Array[Byte]]())
        config.set(ConfKeys.im_paideia_staking_emission_delay,4L)
        config.set(ConfKeys.im_paideia_dao_tokenid,ErgoId.create(daoTokenId).getBytes())
        config.set(ConfKeys.im_paideia_dao_action_tokenid,ErgoId.create(Util.randomKey).getBytes())
        config.set(ConfKeys.im_paideia_staking_emission_amount,100000L)
        config.set(ConfKeys.im_paideia_staking_cyclelength,1000000L)
        config.set(ConfKeys.im_paideia_staking_profit_thresholds,Array(0L,0L))

        val dao = new DAO(daoKey,config)
        Paideia.addDAO(dao)
        TotalStakingState(daoKey, 0L)
        dao
    }
}
