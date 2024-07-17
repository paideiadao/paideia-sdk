package im.paideia.staking.contracts

import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.staking.boxes.SplitProfitBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoToken
import im.paideia.common.events.BlockEvent
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.staking.transactions.SplitProfitTransaction
import im.paideia.Paideia
import scala.collection.mutable.HashMap
import org.ergoplatform.sdk.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.common.events.CreateTransactionsEvent
import org.ergoplatform.appkit.InputBox
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant

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

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      val b = SplitProfitBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        if ((cte.height > lastProfitSplit + 30) || getUtxoSet.size > 50) {
          lastProfitSplit = cte.height.toInt
          if (
            getUtxoSet.size > 0 && getUtxoSet
              .map(boxes(_))
              .take(50)
              .foldLeft(0L)((z: Long, b: InputBox) => z + b.getValue()) >= 3000000L
          ) {
            PaideiaEventResponse(
              1,
              List(
                SplitProfitTransaction(
                  cte.ctx,
                  getUtxoSet
                    .map(boxes(_))
                    .take(50)
                    .toList,
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
    PaideiaEventResponse.merge(List(super.handleEvent(event), response))
  }

  override lazy val parameters: Map[String, Constant[SType]] = {
    val cons = new HashMap[String, Constant[SType]]()
    cons.put(
      "imPaideiaDaoKey",
      ByteArrayConstant(ErgoId.create(contractSig.daoKey).getBytes)
    )
    cons.put(
      "stakeStateTokenId",
      ByteArrayConstant(
        Paideia
          .getConfig(contractSig.daoKey)
          .getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid)
      )
    )
    cons.toMap
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_TREASURY",
      ConfKeys.im_paideia_contracts_treasury.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_STATE",
      ConfKeys.im_paideia_contracts_staking_state.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_PROFIT_SHARING_PCT",
      ConfKeys.im_paideia_staking_profit_share_pct.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_GOVERNANCE_TOKENID",
      ConfKeys.im_paideia_dao_tokenid.ergoValue.getValue()
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
