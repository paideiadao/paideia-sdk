package im.paideia.util

import im.paideia.DAOConfigKey
import java.nio.charset.StandardCharsets

object ConfKeys {
  val im_paideia_default_treasury = DAOConfigKey("im.paideia.default.treasury")
  val im_paideia_default_treasury_signature = DAOConfigKey(
    "im.paideia.default.treasury.signature"
  )
  val im_paideia_default_config = DAOConfigKey("im.paideia.default.config")

  val im_paideia_default_config_signature = DAOConfigKey(
    "im.paideia.default.config.signature"
  )
  val im_paideia_default_action_sendfunds = DAOConfigKey(
    "im.paideia.default.action.sendfunds"
  )
  val im_paideia_default_action_sendfunds_signature = DAOConfigKey(
    "im.paideia.default.action.sendfunds.signature"
  )
  val im_paideia_default_action_updateconfig = DAOConfigKey(
    "im.paideia.default.action.updateconfig"
  )
  val im_paideia_default_action_updateconfig_signature = DAOConfigKey(
    "im.paideia.default.action.updateconfig.signature"
  )
  val im_paideia_default_proposal_basic = DAOConfigKey(
    "im.paideia.default.proposal.basic"
  )
  val im_paideia_default_proposal_basic_signature = DAOConfigKey(
    "im.paideia.default.proposal.basic.signature"
  )
  val im_paideia_default_staking_change = DAOConfigKey(
    "im.paideia.default.staking.change"
  )
  val im_paideia_default_staking_change_signature = DAOConfigKey(
    "im.paideia.default.staking.change.signature"
  )
  val im_paideia_default_staking_stake = DAOConfigKey("im.paideia.default.staking.stake")
  val im_paideia_default_staking_stake_signature = DAOConfigKey(
    "im.paideia.default.staking.stake.signature"
  )
  val im_paideia_default_staking_compound = DAOConfigKey(
    "im.paideia.default.staking.compound"
  )
  val im_paideia_default_staking_compound_signature = DAOConfigKey(
    "im.paideia.default.staking.compound.signature"
  )
  val im_paideia_default_staking_profitshare = DAOConfigKey(
    "im.paideia.default.staking.profitshare"
  )
  val im_paideia_default_staking_profitshare_signature = DAOConfigKey(
    "im.paideia.default.staking.profitshare.signature"
  )
  val im_paideia_default_staking_snapshot = DAOConfigKey(
    "im.paideia.default.staking.snapshot"
  )
  val im_paideia_default_staking_snapshot_signature = DAOConfigKey(
    "im.paideia.default.staking.snapshot.signature"
  )
  val im_paideia_default_staking_state = DAOConfigKey("im.paideia.default.staking.state")
  val im_paideia_default_staking_state_signature = DAOConfigKey(
    "im.paideia.default.staking.state.signature"
  )
  val im_paideia_default_staking_vote = DAOConfigKey("im.paideia.default.staking.vote")
  val im_paideia_default_staking_vote_signature = DAOConfigKey(
    "im.paideia.default.staking.vote.signature"
  )
  val im_paideia_default_staking_unstake = DAOConfigKey(
    "im.paideia.default.staking.unstake"
  )
  val im_paideia_default_staking_unstake_signature = DAOConfigKey(
    "im.paideia.default.staking.unstake.signature"
  )
  val im_paideia_fees_createdao_erg = DAOConfigKey("im.paideia.fees.createdao.erg")

  val im_paideia_fees_createdao_paideia = DAOConfigKey(
    "im.paideia.fees.createdao.paideia"
  )

  val im_paideia_fees_createproposal_paideia = DAOConfigKey(
    "im.paideia.fees.createproposal.paideia"
  )
  val im_paideia_fees_emit_paideia = DAOConfigKey("im.paideia.fees.emit.paideia")

  val im_paideia_fees_emit_operator_paideia = DAOConfigKey(
    "im.paideia.fees.emit.operator.paideia"
  )

  val im_paideia_fees_operator_max_erg = DAOConfigKey(
    "im.paideia.fees.operator.max.erg"
  )

  val im_paideia_fees_compound_operator_paideia = DAOConfigKey(
    "im.paideia.fees.compound.operator.paideia"
  )
  val im_paideia_contracts_protodao = DAOConfigKey("im.paideia.contracts.protodao")

  val im_paideia_contracts_protodaoproxy = DAOConfigKey(
    "im.paideia.contracts.protodaoproxy"
  )
  val im_paideia_contracts_mint      = DAOConfigKey("im.paideia.contracts.mint")
  val im_paideia_contracts_dao       = DAOConfigKey("im.paideia.contracts.dao")
  val im_paideia_contracts_config    = DAOConfigKey("im.paideia.contracts.config")
  val im_paideia_contracts_treasury  = DAOConfigKey("im.paideia.contracts.treasury")
  val im_paideia_contracts_createdao = DAOConfigKey("im.paideia.contracts.createdao")
  val im_paideia_contracts_staking_state = DAOConfigKey(
    "im.paideia.contracts.staking.state"
  )
  val im_paideia_contracts_staking_stake = DAOConfigKey(
    "im.paideia.contracts.staking.stake"
  )
  val im_paideia_contracts_staking_changestake = DAOConfigKey(
    "im.paideia.contracts.staking.changestake"
  )
  val im_paideia_contracts_staking_unstake = DAOConfigKey(
    "im.paideia.contracts.staking.unstake"
  )
  val im_paideia_contracts_staking_snapshot = DAOConfigKey(
    "im.paideia.contracts.staking.snapshot"
  )
  val im_paideia_contracts_staking_vote = DAOConfigKey(
    "im.paideia.contracts.staking.vote"
  )
  val im_paideia_contracts_staking_compound = DAOConfigKey(
    "im.paideia.contracts.staking.compound"
  )
  val im_paideia_contracts_staking_profitshare = DAOConfigKey(
    "im.paideia.contracts.staking.profitshare"
  )

  val im_paideia_contracts_split_profit = DAOConfigKey(
    "im.paideia.contracts.split.profit"
  )

  val im_paideia_contracts_vote        = DAOConfigKey("im.paideia.contracts.vote")
  val im_paideia_dao_name              = DAOConfigKey("im.paideia.dao.name")
  val im_paideia_dao_url               = DAOConfigKey("im.paideia.dao.url")
  val im_paideia_dao_description       = DAOConfigKey("im.paideia.dao.desc")
  val im_paideia_dao_theme             = DAOConfigKey("im.paideia.dao.theme")
  val im_paideia_dao_logo              = DAOConfigKey("im.paideia.dao.logo")
  val im_paideia_dao_banner            = DAOConfigKey("im.paideia.dao.banner")
  val im_paideia_dao_banner_enabled    = DAOConfigKey("im.paideia.dao.banner.enabled")
  val im_paideia_dao_footer            = DAOConfigKey("im.paideia.dao.footer")
  val im_paideia_dao_footer_enabled    = DAOConfigKey("im.paideia.dao.footer.enabled")
  val im_paideia_dao_tokenid           = DAOConfigKey("im.paideia.dao.tokenid")
  val im_paideia_dao_vote_tokenid      = DAOConfigKey("im.paideia.dao.vote.tokenid")
  val im_paideia_dao_proposal_tokenid  = DAOConfigKey("im.paideia.dao.proposal.tokenid")
  val im_paideia_dao_action_tokenid    = DAOConfigKey("im.paideia.dao.action.tokenid")
  val im_paideia_dao_key               = DAOConfigKey("im.paideia.dao.key")
  val im_paideia_dao_quorum            = DAOConfigKey("im.paideia.dao.quorum")
  val im_paideia_dao_threshold         = DAOConfigKey("im.paideia.dao.threshold")
  val im_paideia_dao_governance_type   = DAOConfigKey("im.paideia.dao.governance.type")
  val im_paideia_dao_min_proposal_time = DAOConfigKey("im.paideia.dao.min.proposal.time")
  val im_paideia_dao_min_stake_proposal = DAOConfigKey(
    "im.paideia.dao.min.stake.proposal"
  )
  val im_paideia_staking_state_tokenid = DAOConfigKey("im.paideia.staking.state.tokenid")

  val im_paideia_staking_emission_amount = DAOConfigKey(
    "im.paideia.staking.emission.amount"
  )

  val im_paideia_staking_emission_delay = DAOConfigKey(
    "im.paideia.staking.emission.delay"
  )
  val im_paideia_staking_cyclelength = DAOConfigKey("im.paideia.staking.cyclelength")

  val im_paideia_staking_profit_tokenids = DAOConfigKey(
    "im.paideia.staking.profit.tokenids"
  )

  val im_paideia_staking_profit_thresholds = DAOConfigKey(
    "im.paideia.staking.profit.thresholds"
  )

  val im_paideia_staking_profit_share_pct = DAOConfigKey(
    "im.paideia.staking.profit.sharepct"
  )
  val im_paideia_staking_weight_pureparticipation = DAOConfigKey(
    "im.paideia.staking.weight.pureparticipation"
  )
  val im_paideia_staking_weight_participation = DAOConfigKey(
    "im.paideia.staking.weight.participation"
  )
  val im_paideia_contracts_proposal_base = "im.paideia.contracts.proposal."

  def im_paideia_contracts_proposal(ergoTreeBytes: Array[Byte]) =
    DAOConfigKey(im_paideia_contracts_proposal_base, ergoTreeBytes)
  val im_paideia_contracts_action_base = "im.paideia.contracts.action."

  def im_paideia_contracts_action(ergoTreeBytes: Array[Byte]) =
    DAOConfigKey(im_paideia_contracts_action_base, ergoTreeBytes)
}
