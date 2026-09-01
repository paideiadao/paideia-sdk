package im.paideia.app

import im.paideia.DAO
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Treasury
import im.paideia.governance.GovernanceType
import im.paideia.governance.contracts.ActionSendFundsBasic
import im.paideia.governance.contracts.ActionUpdateConfig
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.governance.contracts.Mint
import im.paideia.governance.contracts.PaideiaOrigin
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.governance.contracts.ProtoDAOProxy
import im.paideia.staking.TotalStakingState
import im.paideia.staking.contracts.ChangeStake
import im.paideia.staking.contracts.SplitProfit
import im.paideia.staking.contracts.Stake
import im.paideia.staking.contracts.StakeCompound
import im.paideia.staking.contracts.StakeProfitShare
import im.paideia.staking.contracts.StakeSnapshot
import im.paideia.staking.contracts.StakeState
import im.paideia.staking.contracts.StakeVote
import im.paideia.staking.contracts.Unstake
import im.paideia.util.ConfKeys
import im.paideia.util.Env
import org.ergoplatform.sdk.ErgoId

/** Seeds the current session's Paideia DAO config tree from genesis values and
  * instantiates every default contract signature - the framework-free port of
  * `PaideiaStateActor.seedGenesis` (paideia-state's `app/actors/PaideiaStateActor.scala`,
  * called from `initializeState` whenever no usable checkpoint is found to restore, so a
  * full archive/chain replay has a starting point to replay on top of).
  *
  * Every `paideiaConfig.set(...)` call below - same key, same order, same value source
  * (`Env.conf`, i.e. whatever `paideia.*` config was loaded for the current session) -
  * and every default-contract instantiation is unchanged from the original, since the
  * resulting config AVL tree's digest must reproduce the on-chain genesis digest
  * byte-for-byte: any reordering, renaming, or value-shifted port would silently fork
  * this session's view of the DAO config away from what the chain actually contains.
  *
  * Must run with the target session bound as `Paideia.current` (e.g. inside
  * `Paideia.withSession(session) { GenesisSeeder.seed() }`, or after
  * `Paideia.setDefault(session)`), on a session whose registries are already empty
  * (fresh, or just `clearRegistries`d) - it unconditionally `addDAO`s as it goes, same as
  * the original.
  *
  * Deviation from the original: the two `logger.info` calls (the computed config digest,
  * and a per-actor contractInstances-size dump) are dropped rather than ported, since
  * this module takes no logging-framework dependency; callers that want that information
  * can read `Paideia.getConfig(Env.paideiaDaoKey)._config.digest` themselves after
  * `seed()` returns.
  */
object GenesisSeeder {

  /** Seeds the Paideia DAO's config tree and every default contract signature into the
    * current session, from `Env.conf` genesis values. See the class scaladoc for what is
    * and isn't ported from `PaideiaStateActor.seedGenesis`.
    */
  def seed(): Unit = {
    val paideiaConfig = DAOConfig(Env.paideiaDaoKey)
    val dummyDaoKey =
      "678441d2c6f7254e6b2f317e45989b42ec3dcd33835b4b03b7c61e9fcc80769c"
    Paideia.addDAO(DAO(dummyDaoKey, paideiaConfig))
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_name,
      Env.conf.getString("im_paideia_dao_name")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_quorum,
      Env.conf.getLong("im_paideia_dao_quorum")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_threshold,
      Env.conf.getLong("im_paideia_dao_threshold")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_tokenid,
      ErgoId
        .create(Env.paideiaTokenId)
        .getBytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_staking_weight_participation,
      Env.conf.getLong("im_paideia_staking_weight_participation").toByte
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_staking_weight_pureparticipation,
      Env.conf.getLong("im_paideia_staking_weight_pureparticipation").toByte
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_staking_cyclelength,
      Env.conf.getLong("im_paideia_staking_cyclelength")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_staking_emission_amount,
      Env.conf.getLong("im_paideia_staking_emission_amount")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_staking_emission_delay,
      Env.conf.getLong("im_paideia_staking_emission_delay")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_staking_profit_share_pct,
      Env.conf.getLong("im_paideia_staking_profit_share_pct").toByte
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_staking_state_tokenid,
      ErgoId
        .create(Env.conf.getString("im_paideia_staking_state_tokenid"))
        .getBytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_fees_createdao_erg,
      Env.conf.getLong("im_paideia_fees_createdao_erg")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_min_proposal_time,
      Env.conf.getLong("im_paideia_dao_min_proposal_time")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_fees_createdao_paideia,
      Env.conf.getLong("im_paideia_fees_createdao_paideia")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_key,
      ErgoId.create(Env.paideiaDaoKey).getBytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_action_tokenid,
      ErgoId
        .create(Env.conf.getString("im_paideia_dao_action_tokenid"))
        .getBytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_proposal_tokenid,
      ErgoId
        .create(Env.conf.getString("im_paideia_dao_proposal_tokenid"))
        .getBytes
    )
    Paideia.addDAO(DAO(Env.paideiaDaoKey, paideiaConfig))
    val configContract = Config(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    val paideiaOriginContract = PaideiaOrigin(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    val protoDaoProxyContract = ProtoDAOProxy(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    val treasuryContract = Treasury(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    val protoDAOContract = ProtoDAO(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    val mintContract = Mint(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    val daoContract = DAOOrigin(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    val splitProfitContract = SplitProfit(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_treasury,
      treasuryContract.contractSignature
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_paideia_origin,
      paideiaOriginContract.contractSignature
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
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_dao,
      daoContract.contractSignature
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_split_profit,
      splitProfitContract.contractSignature
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_fees_createproposal_paideia,
      Env.conf.getLong("im_paideia_fees_createproposal_paideia")
    )

    val defaultTreasuryContract = Treasury(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_treasury,
      treasuryContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_treasury_signature,
      treasuryContract.contractSignature
    )
    val defaultConfigContract = Config(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_config,
      configContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_config_signature,
      configContract.contractSignature
    )
    val defaultActionSendFundsContract = ActionSendFundsBasic(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_action_sendfunds,
      defaultActionSendFundsContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_action_sendfunds_signature,
      defaultActionSendFundsContract.contractSignature
    )
    val defaultActionUpdateConfigContract = ActionUpdateConfig(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_action_updateconfig,
      defaultActionUpdateConfigContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_action_updateconfig_signature,
      defaultActionUpdateConfigContract.contractSignature
    )
    val defaultProposalBasicContract = ProposalBasic(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_proposal_basic,
      defaultProposalBasicContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_proposal_basic_signature,
      defaultProposalBasicContract.contractSignature
    )
    val defaultStakingChangeContract = ChangeStake(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_change,
      defaultStakingChangeContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_change_signature,
      defaultStakingChangeContract.contractSignature
    )
    val defaultStakingStakeContract = Stake(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_stake,
      defaultStakingStakeContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_stake_signature,
      defaultStakingStakeContract.contractSignature
    )
    val defaultStakingCompoundContract = StakeCompound(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_compound,
      defaultStakingCompoundContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_compound_signature,
      defaultStakingCompoundContract.contractSignature
    )
    val defaultStakingProfitshareContract = StakeProfitShare(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_profitshare,
      defaultStakingProfitshareContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_profitshare_signature,
      defaultStakingProfitshareContract.contractSignature
    )
    val defaultStakingSnapshotContract = StakeSnapshot(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_snapshot,
      defaultStakingSnapshotContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_snapshot_signature,
      defaultStakingSnapshotContract.contractSignature
    )
    val defaultStakingStateContract = StakeState(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_state,
      defaultStakingStateContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_state_signature,
      defaultStakingStateContract.contractSignature
    )
    val defaultStakingVoteContract = StakeVote(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_vote,
      defaultStakingVoteContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_vote_signature,
      defaultStakingVoteContract.contractSignature
    )
    val defaultStakingUnstakeContract = Unstake(
      PaideiaContractSignature(version = "1.0.0", daoKey = dummyDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_unstake,
      defaultStakingUnstakeContract.ergoTree.bytes
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_default_staking_unstake_signature,
      defaultStakingUnstakeContract.contractSignature
    )

    paideiaConfig.set(
      ConfKeys.im_paideia_fees_compound_operator_paideia,
      Env.conf.getLong("im_paideia_fees_compound_operator_paideia")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_fees_emit_paideia,
      Env.conf.getLong("im_paideia_fees_emit_paideia")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_fees_emit_operator_paideia,
      Env.conf.getLong("im_paideia_fees_emit_operator_paideia")
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_fees_operator_max_erg,
      Env.conf.getLong("im_paideia_fees_operator_max_erg")
    )
    val stakeStakeContract = Stake(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_staking_stake,
      stakeStakeContract.contractSignature
    )
    val stakeChangeContract = ChangeStake(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_staking_changestake,
      stakeChangeContract.contractSignature
    )
    val stakeUnstakeContract = Unstake(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_staking_unstake,
      stakeUnstakeContract.contractSignature
    )
    val stakeSnapshotContract = StakeSnapshot(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_staking_snapshot,
      stakeSnapshotContract.contractSignature
    )
    val stakeVoteContract = StakeVote(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_staking_vote,
      stakeVoteContract.contractSignature
    )
    val stakeCompoundContract = StakeCompound(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_staking_compound,
      stakeCompoundContract.contractSignature
    )
    val stakeProfitshareContract = StakeProfitShare(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_staking_profitshare,
      stakeProfitshareContract.contractSignature
    )
    val stakeStateContract = StakeState(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_staking_state,
      stakeStateContract.contractSignature
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_governance_type,
      GovernanceType.DEFAULT.id.toByte
    )
    val proposalContract = ProposalBasic(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_proposal(proposalContract.ergoTree.bytes),
      proposalContract.contractSignature
    )
    val sendFundsContract = ActionSendFundsBasic(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_action(sendFundsContract.ergoTree.bytes),
      sendFundsContract.contractSignature
    )
    val updateConfigContract = ActionUpdateConfig(
      PaideiaContractSignature(version = "1.0.0", daoKey = Env.paideiaDaoKey)
    )
    paideiaConfig.set(
      ConfKeys.im_paideia_contracts_action(updateConfigContract.ergoTree.bytes),
      updateConfigContract.contractSignature
    )
    TotalStakingState(Env.paideiaDaoKey, Env.conf.getLong("emission_start"))
    Paideia._daoMap.remove(dummyDaoKey)
    Paideia._actorList.foreach((f: (String, PaideiaActor)) =>
      f._2.contractInstances
        .filter((p: (List[Byte], PaideiaContract)) =>
          p._2.contractSignature.daoKey == dummyDaoKey
        )
        .foreach((p: (List[Byte], PaideiaContract)) =>
          f._2.contractInstances.remove(p._1)
        )
    )
  }
}
