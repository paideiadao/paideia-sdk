package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.governance.boxes.ProtoDAOProxyBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.governance.transactions.CreateProtoDAOTransaction
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import im.paideia.common.events.PaideiaEvent
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Address
import java.util.HashMap
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAOConfigKey
import org.ergoplatform.appkit.ErgoId
import java.nio.charset.StandardCharsets
import im.paideia.util.ConfKeys
import im.paideia.governance.GovernanceType
import io.getblok.getblok_plasma.collections.PlasmaMap
import sigmastate.AvlTreeFlags
import io.getblok.getblok_plasma.PlasmaParameters

class ProtoDAOProxy(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    paideiaDaoConfig: DAOConfig,
    daoName: String,
    daoGovernanceTokenId: String,
    stakePoolSize: Long,
    governanceType: GovernanceType.Value,
    quorum: Byte,
    threshold: Byte,
    stakingEmissionAmount: Long,
    stakingEmissionDelay: Byte,
    stakingCycleLength: Long,
    stakingProfitSharePct: Byte
  ): ProtoDAOProxyBox = {
    ProtoDAOProxyBox(
      ctx,
      paideiaDaoConfig,
      this,
      daoName,
      daoGovernanceTokenId,
      stakePoolSize,
      governanceType,
      quorum,
      threshold,
      stakingEmissionAmount,
      stakingEmissionDelay,
      stakingCycleLength,
      stakingProfitSharePct
    )
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case te: TransactionEvent => {
        PaideiaEventResponse.merge(
          te.tx
            .getOutputs()
            .asScala
            .map { (eto: ErgoTransactionOutput) =>
              {
                if (eto.getErgoTree() == ergoTree.bytesHex) {
                  PaideiaEventResponse(
                    1,
                    List(
                      CreateProtoDAOTransaction(
                        te.ctx,
                        new InputBoxImpl(eto),
                        Address.create(Env.operatorAddress).getErgoAddress
                      )
                    )
                  )
                } else {
                  PaideiaEventResponse(0)
                }
              }
            }
            .toList
        )
      }
      case _ => PaideiaEventResponse(0)
    }
    val superResponse = super.handleEvent(event)
    response
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_PROTODAO",
      ConfKeys.im_paideia_contracts_protodao.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_MINT",
      ConfKeys.im_paideia_contracts_mint.ergoValue.getValue()
    )
    cons.put("_PAIDEIA_DAO_KEY", ErgoId.create(Env.paideiaDaoKey).getBytes())
    cons.put(
      "_EMPTY_CONFIG_DIGEST",
      ErgoValue
        .of(
          new PlasmaMap[DAOConfigKey, Array[Byte]](
            AvlTreeFlags.AllOperationsAllowed,
            PlasmaParameters.default
          ).digest.array
        )
        .getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_NAME",
      ConfKeys.im_paideia_dao_name.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_GOVERNANCE_TOKEN_ID",
      ConfKeys.im_paideia_dao_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_KEY",
      ConfKeys.im_paideia_dao_key.ergoValue.getValue()
    )
    cons.put(
      "_DAO_KEY",
      ErgoValue.of(" DAO Key".getBytes(StandardCharsets.UTF_8)).getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_GOVERNANCE_TYPE",
      ConfKeys.im_paideia_dao_governance_type.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_QUORUM",
      ConfKeys.im_paideia_dao_quorum.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_THRESHOLD",
      ConfKeys.im_paideia_dao_threshold.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_EMISSION_AMOUNT",
      ConfKeys.im_paideia_staking_emission_amount.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_EMISSION_DELAY",
      ConfKeys.im_paideia_staking_emission_delay.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_CYCLE_LENGTH",
      ConfKeys.im_paideia_staking_cyclelength.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_PROFITSHARE_PCT",
      ConfKeys.im_paideia_staking_profit_share_pct.ergoValue.getValue()
    )
    cons
  }
}

object ProtoDAOProxy extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): ProtoDAOProxy =
    getContractInstance[ProtoDAOProxy](
      contractSignature,
      new ProtoDAOProxy(contractSignature)
    )
}
