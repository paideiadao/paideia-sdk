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
  val im_paideia_contracts_mint     = DAOConfigKey("im.paideia.contracts.mint")
  val im_paideia_contracts_dao      = DAOConfigKey("im.paideia.contracts.dao")
  val im_paideia_contracts_config   = DAOConfigKey("im.paideia.contracts.config")
  val im_paideia_contracts_treasury = DAOConfigKey("im.paideia.contracts.treasury")
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

  val im_paideia_contracts_operatorincentive = DAOConfigKey(
    "im.paideia.contracts.operatorincentive"
  )
  val im_paideia_contracts_vote        = DAOConfigKey("im.paideia.contracts.vote")
  val im_paideia_dao_name              = DAOConfigKey("im.paideia.dao.name")
  val im_paideia_dao_tokenid           = DAOConfigKey("im.paideia.dao.tokenid")
  val im_paideia_dao_vote_tokenid      = DAOConfigKey("im.paideia.dao.vote.tokenid")
  val im_paideia_dao_proposal_tokenid  = DAOConfigKey("im.paideia.dao.proposal.tokenid")
  val im_paideia_dao_action_tokenid    = DAOConfigKey("im.paideia.dao.action.tokenid")
  val im_paideia_dao_key               = DAOConfigKey("im.paideia.dao.key")
  val im_paideia_dao_quorum            = DAOConfigKey("im.paideia.dao.quorum")
  val im_paideia_dao_threshold         = DAOConfigKey("im.paideia.dao.threshold")
  val im_paideia_dao_governance_type   = DAOConfigKey("im.paideia.dao.governance.type")
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
  val im_paideia_contracts_proposal_base = "im.paideia.contracts.proposal."

  def im_paideia_contracts_proposal(ergoTreeBytes: Array[Byte]) =
    DAOConfigKey(im_paideia_contracts_proposal_base, ergoTreeBytes)
  val im_paideia_contracts_action_base = "im.paideia.contracts.action."

  def im_paideia_contracts_action(ergoTreeBytes: Array[Byte]) =
    DAOConfigKey(im_paideia_contracts_action_base, ergoTreeBytes)
}
