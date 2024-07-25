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
import im.paideia.staking.boxes.AddStakeProxyBox
import im.paideia.Paideia
import im.paideia.staking.transactions.AddStakeTransaction
import im.paideia.util.ConfKeys
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.transactions.RefundTransaction
import sigma.Coll
import sigma.ast.ConstantPlaceholder
import sigma.ast.SCollection
import sigma.ast.SByte
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import sigma.Colls
import org.ergoplatform.appkit.ContextVar
import scorex.crypto.authds.ADDigest
import sigma.AvlTree
import org.ergoplatform.appkit.InputBox

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
      case cte: CreateTransactionsEvent =>
        PaideiaEventResponse.merge(
          getUtxoSet.toList.map { b =>
            {
              PaideiaEventResponse(
                1,
                List(
                  if (boxes(b).getCreationHeight() < cte.height - 30) {
                    RefundTransaction(
                      cte.ctx,
                      boxes(b),
                      Address
                        .fromPropositionBytes(
                          cte.ctx.getNetworkType(),
                          boxes(b)
                            .getRegisters()
                            .get(0)
                            .getValue()
                            .asInstanceOf[Coll[Byte]]
                            .toArray
                        )
                    )
                  } else {
                    val unsigned = AddStakeTransaction(
                      cte.ctx,
                      boxes(b)
                        .getRegisters()
                        .get(1)
                        .getValue()
                        .asInstanceOf[Long],
                      boxes(b).getTokens().get(0).getId.toString(),
                      Address.create(Env.operatorAddress),
                      Address.fromPropositionBytes(
                        NetworkType.MAINNET,
                        boxes(b)
                          .getRegisters()
                          .get(0)
                          .getValue()
                          .asInstanceOf[Coll[Byte]]
                          .toArray
                      ),
                      contractSignature.daoKey,
                      null
                    )
                    val proxyContextVars = List(
                      ContextVar.of(
                        0.toByte,
                        Paideia
                          .getConfig(contractSignature.daoKey)
                          .getProof(
                            ConfKeys.im_paideia_staking_state_tokenid
                          )(
                            Some(
                              ADDigest @@ unsigned
                                .dataInputs(0)
                                .getRegisters()
                                .get(0)
                                .getValue()
                                .asInstanceOf[AvlTree]
                                .digest
                                .toArray
                            )
                          )
                      ),
                      ContextVar.of(
                        1.toByte,
                        unsigned.stakingContextVars.companionContextVars(0).getValue()
                      ),
                      ContextVar.of(
                        2.toByte,
                        unsigned.stakingContextVars.companionContextVars(1).getValue()
                      )
                    )
                    unsigned.userInputs =
                      List(boxes(b).withContextVars(proxyContextVars: _*))
                    unsigned
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

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      // val b = AddStakeProxyBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_STAKING_STATE_TOKENID",
      ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue()
    )
    cons
  }

  override lazy val parameters: Map[String, Constant[SType]] = {
    val params = new scala.collection.mutable.HashMap[String, Constant[SType]]()
    params.put(
      "imPaideiaDaoKey",
      ByteArrayConstant(
        Colls.fromArray(
          ErgoId.create(contractSignature.daoKey).getBytes
        )
      )
    )
    params.toMap
  }
}

object AddStakeProxy extends PaideiaActor {
  override def apply(
    configKey: DAOConfigKey,
    daoKey: String,
    digest: Option[ADDigest] = None
  ): AddStakeProxy =
    contractFromConfig(configKey, daoKey, digest)

  override def apply(contractSignature: PaideiaContractSignature): AddStakeProxy =
    getContractInstance[AddStakeProxy](
      contractSignature,
      new AddStakeProxy(contractSignature)
    )
}
