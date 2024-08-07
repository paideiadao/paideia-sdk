package im.paideia.staking

import im.paideia.DAO
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoId
import im.paideia.Paideia
import im.paideia.common.contracts.Treasury
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.staking.contracts._

object StakingTest {

  def testDAO: DAO = {
    val daoKey            = Util.randomKey
    val config            = DAOConfig(daoKey)
    val stakeStateTokenId = Util.randomKey
    val daoTokenId        = Util.randomKey

    config.set(ConfKeys.im_paideia_dao_name, "Test DAO")
    config.set(
      ConfKeys.im_paideia_staking_state_tokenid,
      ErgoId.create(stakeStateTokenId).getBytes
    )
    config.set(ConfKeys.im_paideia_staking_emission_delay, 2L)
    config.set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoTokenId).getBytes)
    config.set(
      ConfKeys.im_paideia_dao_action_tokenid,
      ErgoId.create(Util.randomKey).getBytes
    )
    config.set(ConfKeys.im_paideia_staking_emission_amount, 1000000000L)
    config.set(ConfKeys.im_paideia_staking_cyclelength, 1000000L)

    val dao = new DAO(daoKey, config)
    Paideia.addDAO(dao)
    TotalStakingState(daoKey, 0L)

    dao.config.set(ConfKeys.im_paideia_dao_min_proposal_time, 3600000L)

    val changeStakeContract = ChangeStake(PaideiaContractSignature(daoKey = daoKey))
    dao.config
      .set(
        ConfKeys.im_paideia_contracts_staking_changestake,
        changeStakeContract.contractSignature
      )
    val stakeContract = Stake(PaideiaContractSignature(daoKey = daoKey))
    dao.config
      .set(
        ConfKeys.im_paideia_contracts_staking_stake,
        stakeContract.contractSignature
      )
    val unstakeContract = Unstake(PaideiaContractSignature(daoKey = daoKey))
    dao.config
      .set(
        ConfKeys.im_paideia_contracts_staking_unstake,
        unstakeContract.contractSignature
      )
    val compoundContract = StakeCompound(PaideiaContractSignature(daoKey = daoKey))
    dao.config
      .set(
        ConfKeys.im_paideia_contracts_staking_compound,
        compoundContract.contractSignature
      )
    val voteContract = StakeVote(PaideiaContractSignature(daoKey = daoKey))
    dao.config
      .set(
        ConfKeys.im_paideia_contracts_staking_vote,
        voteContract.contractSignature
      )
    val snapshotContract = StakeSnapshot(PaideiaContractSignature(daoKey = daoKey))
    dao.config
      .set(
        ConfKeys.im_paideia_contracts_staking_snapshot,
        snapshotContract.contractSignature
      )
    val profitShareContract = StakeProfitShare(PaideiaContractSignature(daoKey = daoKey))
    dao.config
      .set(
        ConfKeys.im_paideia_contracts_staking_profitshare,
        profitShareContract.contractSignature
      )
    val stakeStateContract = StakeState(PaideiaContractSignature(daoKey = daoKey))
    dao.config
      .set(
        ConfKeys.im_paideia_contracts_staking_state,
        stakeStateContract.contractSignature
      )

    dao
  }
}
