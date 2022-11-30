package im.paideia.util

import im.paideia.DAOConfigKey

object ConfKeys {
    val im_paideia_fees_createdao_erg = DAOConfigKey("im.paideia.fees.createdao.erg")
    val im_paideia_fees_createdao_paideia = DAOConfigKey("im.paideia.fees.createdao.paideia")
    val im_paideia_fees_createproposal_paideia = DAOConfigKey("im.paideia.fees.createproposal.paideia")
    val im_paideia_contracts_protodao = DAOConfigKey("im.paideia.contracts.protodao")
    val im_paideia_contracts_protodaoproxy = DAOConfigKey("im.paideia.contracts.protodaoproxy")
    val im_paideia_contracts_mint = DAOConfigKey("im.paideia.contracts.mint")
    val im_paideia_contracts_dao = DAOConfigKey("im.paideia.contracts.dao")
    val im_paideia_contracts_config = DAOConfigKey("im.paideia.contracts.config")
    val im_paideia_contracts_treasury = DAOConfigKey("im.paideia.contracts.treasury")
    val im_paideia_contracts_staking = DAOConfigKey("im.paideia.contracts.staking")
    val im_paideia_contracts_operatorincentive = DAOConfigKey("im.paideia.contracts.operatorincentive")
    val im_paideia_contracts_vote = DAOConfigKey("im.paideia.contracts.vote")
    val im_paideia_dao_name = DAOConfigKey("im.paideia.dao.name")
    val im_paideia_dao_tokenid = DAOConfigKey("im.paideia.dao.tokenid")
    val im_paideia_dao_vote_tokenid = DAOConfigKey("im.paideia.dao.vote.tokenid")
    val im_paideia_dao_proposal_tokenid = DAOConfigKey("im.paideia.dao.proposal.tokenid")
    val im_paideia_dao_action_tokenid = DAOConfigKey("im.paideia.dao.action.tokenid")
    val im_paideia_dao_key = DAOConfigKey("im.paideia.dao.key")
    val im_paideia_dao_quorum = DAOConfigKey("im.paideia.dao.quorum")
    val im_paideia_staking_state_tokenid = DAOConfigKey("im.paideia.staking.state.tokenid")
    val im_paideia_staking_emission_amount = DAOConfigKey("im.paideia.staking.emission.amount")
    val im_paideia_staking_emission_delay = DAOConfigKey("im.paideia.staking.emission.delay")
    val im_paideia_staking_cyclelength = DAOConfigKey("im.paideia.staking.cyclelength")
    val im_paideia_staking_profit_tokenids = DAOConfigKey("im.paideia.staking.profit.tokenids")
    val im_paideia_staking_profit_thresholds = DAOConfigKey("im.paideia.staking.profit.thresholds")
    val im_paideia_contracts_proposal_base = "im.paideia.contracts.proposal."
    def im_paideia_contracts_proposal(contractHash: String) = DAOConfigKey(im_paideia_contracts_proposal_base++contractHash)
    val im_paideia_contracts_action_base = "im.paideia.contracts.action."
    def im_paideia_contracts_action(contractHash: String) = DAOConfigKey(im_paideia_contracts_action_base++contractHash)
}
