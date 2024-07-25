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
import im.paideia.DAOConfigKey
import scorex.util.encode.Base16
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigma.Colls
import scorex.crypto.hash.Blake2b256
import sigma.Coll

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
    val cons  = new HashMap[String, Object]()
    val step1 = getAvlTreeKeys().toArray
    val step2 = step1.map(cb1 => cb1.toArray)
    val step3 = step2.flatten
    val step4 = step3.map(_.toArray)
    val step5 = step4.flatten
    cons.put(
      "_AVL_TREE_KEYS_HASH",
      Colls.fromArray(
        Blake2b256.hash(
          step5
        )
      )
    )
    cons
  }

  def getInsertKeys(): Array[Array[Byte]] = {
    Array(
      ConfKeys.im_paideia_contracts_treasury.hashedKey,
      ConfKeys.im_paideia_contracts_config.hashedKey,
      ConfKeys.im_paideia_contracts_action_base.getBytes(),
      ConfKeys.im_paideia_contracts_proposal_base.getBytes(),
      ConfKeys.im_paideia_contracts_staking_changestake.hashedKey,
      ConfKeys.im_paideia_contracts_staking_stake.hashedKey,
      ConfKeys.im_paideia_contracts_staking_compound.hashedKey,
      ConfKeys.im_paideia_contracts_staking_profitshare.hashedKey,
      ConfKeys.im_paideia_contracts_staking_snapshot.hashedKey,
      ConfKeys.im_paideia_contracts_staking_state.hashedKey,
      ConfKeys.im_paideia_contracts_staking_vote.hashedKey,
      ConfKeys.im_paideia_contracts_staking_unstake.hashedKey,
      ConfKeys.im_paideia_contracts_dao.hashedKey
    )
  }

  def getAvlTreeKeys(): Coll[Coll[Coll[Byte]]] = {

    Colls.fromArray(
      Array(
        Colls.fromArray(
          getConfigKeys()
            .map((dck: DAOConfigKey) => Colls.fromArray(dck.hashedKey))
        ),
        Colls.fromArray(
          getDAOConfigKeys()
            .map((dck: DAOConfigKey) => Colls.fromArray(dck.hashedKey))
        ),
        Colls.fromArray(
          getInsertKeys()
            .map((b: Array[Byte]) => Colls.fromArray(b))
        )
      )
    )

  }

  def getInsertOperations(dao: DAO) = {
    val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)
    val configContract = Config(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_config_signature
      ).withDaoKey(dao.key)
    )
    val treasuryContract = Treasury(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_treasury_signature
      ).withDaoKey(dao.key)
    )
    val actionSendFundsContract = ActionSendFundsBasic(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_action_sendfunds_signature
      ).withDaoKey(dao.key)
    )
    val actionUpdateConfigContract = ActionUpdateConfig(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_action_updateconfig_signature
      ).withDaoKey(dao.key)
    )
    val proposalBasicContract = ProposalBasic(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_proposal_basic_signature
      ).withDaoKey(dao.key)
    )
    val stakingChangeContract = ChangeStake(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_staking_change_signature
      ).withDaoKey(dao.key)
    )
    val stakingStakeContract = Stake(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_staking_stake_signature
      ).withDaoKey(dao.key)
    )
    val stakingCompoundContract = StakeCompound(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_staking_compound_signature
      ).withDaoKey(dao.key)
    )
    val stakingProfitShareContract = StakeProfitShare(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_staking_profitshare_signature
      ).withDaoKey(dao.key)
    )
    val stakingSnapshotContract = StakeSnapshot(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_staking_snapshot_signature
      ).withDaoKey(dao.key)
    )
    val stakingVoteContract = StakeVote(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_staking_vote_signature
      ).withDaoKey(dao.key)
    )
    val stakingUnstakeContract = Unstake(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_staking_unstake_signature
      ).withDaoKey(dao.key)
    )
    val stakeStateContract = StakeState(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_staking_state_signature
      ).withDaoKey(dao.key)
    )
    val daoOriginContract = DAOOrigin(
      paideiaConfig[PaideiaContractSignature](
        ConfKeys.im_paideia_default_dao_signature
      ).withDaoKey(dao.key)
    )
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
      ),
      (
        ConfKeys.im_paideia_contracts_dao,
        DAOConfigValueSerializer(daoOriginContract.contractSignature)
      )
    )
  }

  def getConfigKeys(): Array[DAOConfigKey] = {
    Array(
      ConfKeys.im_paideia_default_dao,
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
      ConfKeys.im_paideia_default_staking_unstake_signature,
      ConfKeys.im_paideia_default_dao_signature
    )
  }

  override def getConfigContext(configDigest: Option[ADDigest]) = Paideia
    .getConfig(contractSignature.daoKey)
    .getProof(
      getConfigKeys(): _*
    )(configDigest)

  def getDAOConfigKeys(): Array[DAOConfigKey] = {
    Array(
      ConfKeys.im_paideia_dao_proposal_tokenid,
      ConfKeys.im_paideia_dao_action_tokenid,
      ConfKeys.im_paideia_dao_key,
      ConfKeys.im_paideia_staking_state_tokenid,
      ConfKeys.im_paideia_staking_cyclelength
    )
  }

  def getDAOConfigContext(daoConfig: DAOConfig, configDigest: Option[ADDigest]) =
    daoConfig
      .getProof(
        getDAOConfigKeys(): _*
      )(configDigest)

}

object CreateDAO extends PaideiaActor {
  override def apply(
    configKey: DAOConfigKey,
    daoKey: String,
    digest: Option[ADDigest] = None
  ): CreateDAO =
    contractFromConfig(configKey, daoKey, digest)
  override def apply(contractSignature: PaideiaContractSignature): CreateDAO =
    getContractInstance[CreateDAO](
      contractSignature,
      new CreateDAO(contractSignature)
    )
}
