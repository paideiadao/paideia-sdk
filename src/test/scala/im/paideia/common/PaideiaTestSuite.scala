package im.paideia.common

import org.scalatest.funsuite.AnyFunSuite
import scala.util.Random
import im.paideia.HttpClientTesting
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.util.Env
import im.paideia.DAO
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts.Config
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.PaideiaOrigin
import org.ergoplatform.sdk.ErgoId
import im.paideia.governance.contracts.ProtoDAOProxy
import im.paideia.common.contracts.Treasury
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.governance.contracts.Mint
import im.paideia.util.ConfKeys
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.util.Util
import im.paideia.staking.contracts.SplitProfit
import im.paideia.staking.contracts.StakeState
import im.paideia.governance.contracts.ActionSendFundsBasic
import im.paideia.governance.contracts.ActionUpdateConfig
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.staking.contracts.ChangeStake
import im.paideia.staking.contracts.Stake
import im.paideia.staking.contracts.StakeCompound
import im.paideia.staking.contracts.StakeProfitShare
import im.paideia.staking.contracts.StakeSnapshot
import im.paideia.staking.contracts.StakeVote
import im.paideia.staking.contracts.Unstake
import im.paideia.governance.contracts.CreateDAO

class PaideiaTestSuite extends AnyFunSuite with HttpClientTesting {}

object PaideiaTestSuite {
  var initializedPaideia: Boolean = false

  def init(ctx: BlockchainContextImpl) = {
    Paideia.clear
    StakeState.contractInstances.clear()
    if (!initializedPaideia) {
      val paideiaConfig = DAOConfig(Env.paideiaDaoKey)
      paideiaConfig.set(ConfKeys.im_paideia_fees_createdao_erg, 1000000000L)
      paideiaConfig.set(ConfKeys.im_paideia_fees_createdao_paideia, 100L)
      paideiaConfig.set(
        ConfKeys.im_paideia_dao_key,
        ErgoId.create(Env.paideiaDaoKey).getBytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_dao_action_tokenid,
        ErgoId.create(Util.randomKey).getBytes
      )
      Paideia.addDAO(DAO(Env.paideiaDaoKey, paideiaConfig))
      val proposalTokenId = Util.randomKey
      paideiaConfig.set(
        ConfKeys.im_paideia_dao_proposal_tokenid,
        ErgoId.create(proposalTokenId).getBytes
      )
      val configContract = Config(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
      val paideiaOriginContract = PaideiaOrigin(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val protoDaoProxyContract = ProtoDAOProxy(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val treasuryContract = Treasury(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val protoDAOContract = ProtoDAO(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val mintContract = Mint(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
      val daoContract  = DAOOrigin(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
      val splitProfitContract = SplitProfit(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val actionSendFundsContract = ActionSendFundsBasic(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val actionUpdateConfigContract = ActionUpdateConfig(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val proposalBasicContract = ProposalBasic(
        PaideiaContractSignature(daoKey = Util.randomKey)
      )
      val stakingChangeContract = ChangeStake(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val stakingStakeContract = Stake(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val stakingCompoundContract = StakeCompound(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val stakingProfitShareContract = StakeProfitShare(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val stakingSnapshotContract = StakeSnapshot(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val stakingStateContract = StakeState(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val stakingVoteContract = StakeVote(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val stakeUnstakeContract = Unstake(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      val createDaoContract = CreateDAO(
        PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_contracts_createdao,
        createDaoContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_contracts_treasury,
        treasuryContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_contracts_protodao,
        protoDAOContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_contracts_protodaoproxy,
        protoDaoProxyContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_contracts_mint,
        mintContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_contracts_config,
        configContract.contractSignature
      )
      paideiaConfig.set(ConfKeys.im_paideia_contracts_dao, daoContract.contractSignature)
      paideiaConfig.set(
        ConfKeys.im_paideia_contracts_split_profit,
        splitProfitContract.contractSignature
      )
      paideiaConfig.set(ConfKeys.im_paideia_fees_createproposal_paideia, 10000L)
      paideiaConfig.set(
        ConfKeys.im_paideia_default_treasury,
        treasuryContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_treasury_signature,
        treasuryContract.contractSignature
      )
      paideiaConfig.set(ConfKeys.im_paideia_default_config, configContract.ergoTree.bytes)
      paideiaConfig.set(
        ConfKeys.im_paideia_default_config_signature,
        configContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_action_sendfunds,
        actionSendFundsContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_action_sendfunds_signature,
        actionSendFundsContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_action_updateconfig,
        actionUpdateConfigContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_action_updateconfig_signature,
        actionUpdateConfigContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_proposal_basic,
        proposalBasicContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_proposal_basic_signature,
        proposalBasicContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_change,
        stakingChangeContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_change_signature,
        stakingChangeContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_stake,
        stakingStakeContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_stake_signature,
        stakingStakeContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_compound,
        stakingCompoundContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_compound_signature,
        stakingCompoundContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_profitshare,
        stakingProfitShareContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_profitshare_signature,
        stakingProfitShareContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_snapshot,
        stakingSnapshotContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_snapshot_signature,
        stakingSnapshotContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_state,
        stakingStateContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_state_signature,
        stakingStateContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_vote,
        stakingVoteContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_vote_signature,
        stakingVoteContract.contractSignature
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_unstake,
        stakeUnstakeContract.ergoTree.bytes
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_default_staking_unstake_signature,
        stakeUnstakeContract.contractSignature
      )
      paideiaConfig.set(ConfKeys.im_paideia_fees_compound_operator_paideia, 1000L)
      paideiaConfig.set(ConfKeys.im_paideia_fees_emit_paideia, 1000L)
      paideiaConfig.set(ConfKeys.im_paideia_fees_emit_operator_paideia, 1000L)
      paideiaConfig.set(ConfKeys.im_paideia_fees_operator_max_erg, 5000000L)
      configContract.newBox(
        configContract.box(ctx, Paideia.getDAO(Env.paideiaDaoKey)).inputBox(),
        false
      )
      paideiaOriginContract.newBox(
        paideiaOriginContract.box(ctx, paideiaConfig, 1000000L).inputBox(),
        false
      )
      createDaoContract.newBox(createDaoContract.box(ctx).inputBox(), false)
      // initializedPaideia = true
    }
  }
}
