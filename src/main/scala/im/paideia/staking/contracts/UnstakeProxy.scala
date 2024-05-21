package im.paideia.staking.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import org.ergoplatform.sdk.ErgoToken
import im.paideia.util.Env
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import im.paideia.common.events.PaideiaEvent
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Address
import scala.collection.mutable.HashMap
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAOConfigKey
import org.ergoplatform.sdk.ErgoId
import java.nio.charset.StandardCharsets
import im.paideia.staking.boxes.UnstakeProxyBox
import im.paideia.Paideia
import im.paideia.staking.transactions.UnstakeTransaction
import im.paideia.staking.StakeRecord
import im.paideia.util.ConfKeys
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.transactions.RefundTransaction
import sigma.Coll
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant

class UnstakeProxy(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    stakeKey: String,
    newStakeRecord: StakeRecord,
    userAddress: String
  ): UnstakeProxyBox = {
    UnstakeProxyBox(
      ctx,
      this,
      Paideia.getConfig(contractSignature.daoKey),
      stakeKey,
      userAddress,
      newStakeRecord
    )
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent =>
        PaideiaEventResponse.merge(
          getUtxoSet.toList
            .map { b =>
              {
                PaideiaEventResponse(
                  1,
                  List(
                    if (boxes(b).getCreationHeight() < cte.height - 30) {
                      RefundTransaction(
                        cte.ctx,
                        boxes(b),
                        Address.fromPropositionBytes(
                          NetworkType.MAINNET,
                          boxes(b)
                            .getRegisters()
                            .get(0)
                            .getValue()
                            .asInstanceOf[Coll[Byte]]
                            .toArray
                        )
                      )
                    } else {
                      UnstakeTransaction(
                        cte.ctx,
                        boxes(b),
                        Address.create(Env.operatorAddress),
                        contractSignature.daoKey
                      )
                    }
                  )
                )
              }
            }
        )
      case _ => PaideiaEventResponse(0)
    }
    PaideiaEventResponse.merge(List(super.handleEvent(event), response))
  }

  override lazy val parameters: Map[String, Constant[SType]] = {
    val cons = new HashMap[String, Constant[SType]]()
    cons.put(
      "imPaideiaDaoKey",
      ByteArrayConstant(ErgoId.create(contractSignature.daoKey).getBytes)
    )
    cons.toMap
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_STAKING_STATE_TOKENID",
      ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_PROFIT_TOKENIDS",
      ConfKeys.im_paideia_staking_profit_tokenids.ergoValue.getValue()
    )
    cons
  }
}

object UnstakeProxy extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): UnstakeProxy =
    getContractInstance[UnstakeProxy](
      contractSignature,
      new UnstakeProxy(contractSignature)
    )
}
