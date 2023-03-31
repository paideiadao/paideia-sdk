package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.boxes.CreateVoteProxyBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.Address
import im.paideia.governance.transactions.CreateVoteTransaction
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import scala.collection.JavaConverters._
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.Paideia
import im.paideia.util.Env
import java.util.HashMap
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoValue
import java.nio.charset.StandardCharsets
import im.paideia.common.events.CreateTransactionsEvent

class CreateVoteProxy(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    stakeKey: String,
    userAddress: Address
  ): CreateVoteProxyBox = {
    CreateVoteProxyBox(ctx, stakeKey, userAddress, this)
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        PaideiaEventResponse.merge(
          getUtxoSet.toList
            .map { b =>
              PaideiaEventResponse(
                1,
                List(
                  CreateVoteTransaction(
                    cte.ctx,
                    boxes(b),
                    Paideia.getDAO(contractSignature.daoKey),
                    Address.create(Env.operatorAddress).getErgoAddress
                  )
                )
              )
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
    cons.put("_IM_PAIDEIA_DAO_NAME", ConfKeys.im_paideia_dao_name.ergoValue.getValue())
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSignature.daoKey).getBytes())
    cons.put(
      "_VOTE_KEY",
      ErgoValue.of(" Vote Key".getBytes(StandardCharsets.UTF_8)).getValue()
    )
    cons
  }
}

object CreateVoteProxy extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): CreateVoteProxy = {
    getContractInstance[CreateVoteProxy](
      contractSignature = contractSignature,
      new CreateVoteProxy(contractSignature)
    )
  }
}
