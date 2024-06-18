package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaActor
import im.paideia.DAOConfig
import scorex.crypto.authds.ADDigest
import im.paideia.util.ConfKeys
import im.paideia.Paideia
import scala.collection.mutable.HashMap
import org.ergoplatform.sdk.ErgoId
import im.paideia.util.Env
import org.ergoplatform.appkit.ErgoValue
import java.nio.charset.StandardCharsets
import im.paideia.governance.boxes.CreateDAOBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfigValueSerializer
import im.paideia.common.contracts.Treasury
import im.paideia.DAO
import im.paideia.staking.contracts.ChangeStake
import im.paideia.staking.contracts.Stake
import im.paideia.staking.contracts.StakeCompound
import im.paideia.staking.contracts.StakeProfitShare
import im.paideia.staking.contracts.StakeSnapshot
import im.paideia.staking.contracts.StakeVote
import im.paideia.staking.contracts.Unstake
import im.paideia.common.contracts.Config
import im.paideia.staking.contracts.StakeState
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import org.ergoplatform.appkit.InputBox

class CreateDAO(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(ctx: BlockchainContextImpl, value: Long) = CreateDAOBox(ctx, this, value)

  override lazy val parameters: Map[String, Constant[SType]] = {
    val cons = new HashMap[String, Constant[SType]]()
    cons.put(
      "paideiaDaoKey",
      ByteArrayConstant(ErgoId.create(Env.paideiaDaoKey).getBytes)
    )
    cons.toMap
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) false
    else true
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_DAO",
      ConfKeys.im_paideia_contracts_dao.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_TREASURY",
      ConfKeys.im_paideia_contracts_treasury.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_CONFIG",
      ConfKeys.im_paideia_contracts_config.ergoValue.getValue()
    )
    cons.put("_IM_PAIDEIA_DAO_KEY", ConfKeys.im_paideia_dao_key.ergoValue.getValue())
    cons.put(
      "_IM_PAIDEIA_DAO_PROPOSAL_TOKENID",
      ConfKeys.im_paideia_dao_proposal_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_ACTION_TOKENID",
      ConfKeys.im_paideia_dao_action_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_STATE_TOKENID",
      ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_TREASURY",
      ConfKeys.im_paideia_default_treasury.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_TREASURY_SIGNATURE",
      ConfKeys.im_paideia_default_treasury_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_CONFIG",
      ConfKeys.im_paideia_default_config.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_CONFIG_SIGNATURE",
      ConfKeys.im_paideia_default_config_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_ACTION_SEND_FUNDS",
      ConfKeys.im_paideia_default_action_sendfunds.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_ACTION_SEND_FUNDS_SIG",
      ConfKeys.im_paideia_default_action_sendfunds_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_ACTION_UPDATE_CONFIG",
      ConfKeys.im_paideia_default_action_updateconfig.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_ACTION_UPDATE_CONFIG_SIG",
      ConfKeys.im_paideia_default_action_updateconfig_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_PROPOSAL_BASIC",
      ConfKeys.im_paideia_default_proposal_basic.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_PROPOSAL_BASIC_SIG",
      ConfKeys.im_paideia_default_proposal_basic_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_CHANGE",
      ConfKeys.im_paideia_default_staking_change.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_CHANGE_SIG",
      ConfKeys.im_paideia_default_staking_change_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKE_STAKE",
      ConfKeys.im_paideia_default_staking_stake.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_STAKE_SIG",
      ConfKeys.im_paideia_default_staking_stake_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_COMPOUND",
      ConfKeys.im_paideia_default_staking_compound.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_COMPOUND_SIG",
      ConfKeys.im_paideia_default_staking_compound_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_PROFITSHARE",
      ConfKeys.im_paideia_default_staking_profitshare.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_PROFITSHARE_SIG",
      ConfKeys.im_paideia_default_staking_profitshare_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_SNAPSHOT",
      ConfKeys.im_paideia_default_staking_snapshot.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_SNAPSHOT_SIG",
      ConfKeys.im_paideia_default_staking_snapshot_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_STATE",
      ConfKeys.im_paideia_default_staking_state.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_STATE_SIG",
      ConfKeys.im_paideia_default_staking_state_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_VOTE",
      ConfKeys.im_paideia_default_staking_vote.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_VOTE_SIG",
      ConfKeys.im_paideia_default_staking_vote_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_UNSTAKE",
      ConfKeys.im_paideia_default_staking_unstake.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_STAKING_UNSTAKE_SIG",
      ConfKeys.im_paideia_default_staking_unstake_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_ACTION",
      ConfKeys.im_paideia_contracts_action_base.getBytes(StandardCharsets.UTF_8)
    )
    cons.put(
      "_IM_PAIDEIA_PROPOSAL",
      ConfKeys.im_paideia_contracts_proposal_base.getBytes(StandardCharsets.UTF_8)
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_CHANGE",
      ConfKeys.im_paideia_contracts_staking_changestake.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_STAKE",
      ConfKeys.im_paideia_contracts_staking_stake.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND",
      ConfKeys.im_paideia_contracts_staking_compound.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_PROFIT_SHARE",
      ConfKeys.im_paideia_contracts_staking_profitshare.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT",
      ConfKeys.im_paideia_contracts_staking_snapshot.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_STATE",
      ConfKeys.im_paideia_contracts_staking_state.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_VOTE",
      ConfKeys.im_paideia_contracts_staking_vote.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE",
      ConfKeys.im_paideia_contracts_staking_unstake.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_CYCLELENGTH",
      ConfKeys.im_paideia_staking_cyclelength.ergoValue.getValue()
    )
    cons
  }

  def getInsertOperations(dao: DAO) = {
    val configContract = Config(
      PaideiaContractSignature(daoKey = dao.key)
    )
    val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
    val actionSendFundsContract = ActionSendFundsBasic(
      PaideiaContractSignature(daoKey = dao.key)
    )
    val actionUpdateConfigContract = ActionUpdateConfig(
      PaideiaContractSignature(daoKey = dao.key)
    )
    val proposalBasicContract = ProposalBasic(
      PaideiaContractSignature(daoKey = dao.key)
    )
    val stakingChangeContract = ChangeStake(PaideiaContractSignature(daoKey = dao.key))
    val stakingStakeContract  = Stake(PaideiaContractSignature(daoKey = dao.key))
    val stakingCompoundContract = StakeCompound(
      PaideiaContractSignature(daoKey = dao.key)
    )
    val stakingProfitShareContract = StakeProfitShare(
      PaideiaContractSignature(daoKey = dao.key)
    )
    val stakingSnapshotContract = StakeSnapshot(
      PaideiaContractSignature(daoKey = dao.key)
    )
    val stakingVoteContract    = StakeVote(PaideiaContractSignature(daoKey = dao.key))
    val stakingUnstakeContract = Unstake(PaideiaContractSignature(daoKey = dao.key))
    val stakeStateContract     = StakeState(PaideiaContractSignature(daoKey = dao.key))
    Array(
      (
        ConfKeys.im_paideia_contracts_treasury,
        DAOConfigValueSerializer(treasuryContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_config,
        DAOConfigValueSerializer(configContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_action(
          actionSendFundsContract.ergoTree.bytes
        ),
        DAOConfigValueSerializer(actionSendFundsContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_action(
          actionUpdateConfigContract.ergoTree.bytes
        ),
        DAOConfigValueSerializer(actionUpdateConfigContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_proposal(
          proposalBasicContract.ergoTree.bytes
        ),
        DAOConfigValueSerializer(proposalBasicContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_staking_changestake,
        DAOConfigValueSerializer(stakingChangeContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_staking_stake,
        DAOConfigValueSerializer(stakingStakeContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_staking_compound,
        DAOConfigValueSerializer(stakingCompoundContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_staking_profitshare,
        DAOConfigValueSerializer(stakingProfitShareContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_staking_snapshot,
        DAOConfigValueSerializer(stakingSnapshotContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_staking_state,
        DAOConfigValueSerializer(stakeStateContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_staking_vote,
        DAOConfigValueSerializer(stakingVoteContract.contractSignature)
      ),
      (
        ConfKeys.im_paideia_contracts_staking_unstake,
        DAOConfigValueSerializer(stakingUnstakeContract.contractSignature)
      )
    )
  }

  override def getConfigContext(configDigest: Option[ADDigest]) = Paideia
    .getConfig(contractSignature.daoKey)
    .getProof(
      ConfKeys.im_paideia_contracts_dao,
      ConfKeys.im_paideia_default_config,
      ConfKeys.im_paideia_default_config_signature,
      ConfKeys.im_paideia_default_treasury,
      ConfKeys.im_paideia_default_treasury_signature,
      ConfKeys.im_paideia_default_action_sendfunds,
      ConfKeys.im_paideia_default_action_sendfunds_signature,
      ConfKeys.im_paideia_default_action_updateconfig,
      ConfKeys.im_paideia_default_action_updateconfig_signature,
      ConfKeys.im_paideia_default_proposal_basic,
      ConfKeys.im_paideia_default_proposal_basic_signature,
      ConfKeys.im_paideia_default_staking_change,
      ConfKeys.im_paideia_default_staking_change_signature,
      ConfKeys.im_paideia_default_staking_stake,
      ConfKeys.im_paideia_default_staking_stake_signature,
      ConfKeys.im_paideia_default_staking_compound,
      ConfKeys.im_paideia_default_staking_compound_signature,
      ConfKeys.im_paideia_default_staking_profitshare,
      ConfKeys.im_paideia_default_staking_profitshare_signature,
      ConfKeys.im_paideia_default_staking_snapshot,
      ConfKeys.im_paideia_default_staking_snapshot_signature,
      ConfKeys.im_paideia_default_staking_state,
      ConfKeys.im_paideia_default_staking_state_signature,
      ConfKeys.im_paideia_default_staking_vote,
      ConfKeys.im_paideia_default_staking_vote_signature,
      ConfKeys.im_paideia_default_staking_unstake,
      ConfKeys.im_paideia_default_staking_unstake_signature
    )(configDigest)

  def getDAOConfigContext(daoConfig: DAOConfig, configDigest: Option[ADDigest]) =
    daoConfig
      .getProof(
        ConfKeys.im_paideia_dao_proposal_tokenid,
        ConfKeys.im_paideia_dao_action_tokenid,
        ConfKeys.im_paideia_dao_key,
        ConfKeys.im_paideia_staking_state_tokenid,
        ConfKeys.im_paideia_staking_cyclelength
      )(configDigest)

}

object CreateDAO extends PaideiaActor {
  override def apply(contractSignature: PaideiaContractSignature): CreateDAO =
    getContractInstance[CreateDAO](
      contractSignature,
      new CreateDAO(contractSignature)
    )
}
