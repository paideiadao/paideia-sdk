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
import org.ergoplatform.appkit.ErgoId
import im.paideia.governance.contracts.ProtoDAOProxy
import im.paideia.common.contracts.Treasury
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.governance.contracts.Mint
import im.paideia.util.ConfKeys
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.util.Util
import im.paideia.staking.contracts.SplitProfit
import im.paideia.staking.contracts.StakeState

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
        ErgoId.create(Env.paideiaDaoKey).getBytes()
      )
      paideiaConfig.set(
        ConfKeys.im_paideia_dao_action_tokenid,
        ErgoId.create(Util.randomKey).getBytes()
      )
      Paideia.addDAO(DAO(Env.paideiaDaoKey, paideiaConfig))
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
      // initializedPaideia = true
    }
  }
}
