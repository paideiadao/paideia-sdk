package im.paideia.staking.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import org.ergoplatform.restapi.client.ErgoTransactionOutput
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
import im.paideia.staking.boxes.AddStakeProxyBox
import im.paideia.Paideia
import im.paideia.staking.transactions.AddStakeTransaction
import im.paideia.util.ConfKeys
import im.paideia.common.events.CreateTransactionsEvent

class AddStakeProxy(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    stakeKey: String,
    addAmount: Long,
    userAddress: String
  ): AddStakeProxyBox = {
    AddStakeProxyBox(
      ctx,
      this,
      Paideia.getConfig(contractSignature.daoKey),
      stakeKey,
      userAddress,
      addAmount
    )
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        PaideiaEventResponse.merge(
          getUtxoSet.toList.map { b =>
            {
              PaideiaEventResponse(
                1,
                List(
                  AddStakeTransaction(
                    cte.ctx,
                    boxes(b),
                    Address.create(Env.operatorAddress).getErgoAddress,
                    contractSignature.daoKey
                  )
                )
              )
            }
          }
        )
      }
      case _ => PaideiaEventResponse(0)
    }
    val superResponse = super.handleEvent(event)
    response
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSignature.daoKey).getBytes())
    cons.put(
      "_IM_PAIDEIA_STAKING_STATE_TOKENID",
      ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue()
    )
    cons
  }
}

object AddStakeProxy extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): AddStakeProxy =
    getContractInstance[AddStakeProxy](
      contractSignature,
      new AddStakeProxy(contractSignature)
    )
}
