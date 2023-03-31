package im.paideia.staking.contracts

import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.staking.boxes.SplitProfitBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.common.events.BlockEvent
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.staking.transactions.SplitProfitTransaction
import im.paideia.Paideia
import java.util.HashMap
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.common.events.CreateTransactionsEvent

class SplitProfit(contractSig: PaideiaContractSignature)
  extends PaideiaContract(contractSig) {

  private var lastProfitSplit: Int = 0

  def box(
    ctx: BlockchainContextImpl,
    value: Long,
    tokens: List[ErgoToken]
  ): SplitProfitBox = {
    SplitProfitBox(ctx, value, tokens, this)
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        if ((cte.height > lastProfitSplit + 30) || boxes.size > 50) {
          lastProfitSplit = cte.height.toInt
          if (boxes.size > 0) {
            PaideiaEventResponse(
              1,
              List(
                SplitProfitTransaction(
                  cte.ctx,
                  boxes.values.take(50).toList,
                  Paideia.getDAO(contractSignature.daoKey)
                )
              )
            )
          } else {
            PaideiaEventResponse(0)
          }
        } else {
          PaideiaEventResponse(0)
        }
      }
      case _ => PaideiaEventResponse(0)
    }
    val superResponse = super.handleEvent(event)
    response
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSig.daoKey).getBytes())
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_TREASURY",
      ConfKeys.im_paideia_contracts_treasury.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING",
      ConfKeys.im_paideia_contracts_staking.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_PROFIT_SHARING_PCT",
      ConfKeys.im_paideia_staking_profit_share_pct.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_GOVERNANCE_TOKENID",
      ConfKeys.im_paideia_dao_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_PROFIT_TOKENIDS",
      ConfKeys.im_paideia_staking_profit_tokenids.ergoValue.getValue()
    )
    cons
  }
}

object SplitProfit extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): SplitProfit =
    getContractInstance[SplitProfit](
      contractSignature,
      new SplitProfit(contractSignature)
    )
}
