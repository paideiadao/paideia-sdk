package im.paideia.staking.contracts

import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.Paideia
import org.ergoplatform.appkit.Address
import im.paideia.staking.boxes.StakeProxyBox
import org.ergoplatform.sdk.ErgoToken
import im.paideia.staking.transactions.StakeTransaction
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.TransactionEvent
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.util.Env
import im.paideia.util.ConfKeys
import java.nio.charset.StandardCharsets
import scala.collection.mutable.HashMap
import org.ergoplatform.sdk.ErgoId
import sigma.Colls
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.transactions.RefundTransaction
import sigma.Coll
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.ContextVar
import scorex.crypto.authds.ADDigest
import sigma.AvlTree
import im.paideia.DAOConfigKey

class StakeProxy(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    userAddress: String,
    stakeAmount: Long,
    stakeDiff: Long = 0L
  ): StakeProxyBox = {
    StakeProxyBox(
      ctx,
      this,
      Paideia.getConfig(contractSignature.daoKey),
      userAddress,
      stakeAmount,
      stakeDiff
    )
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      // val b = StakeProxyBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
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
                    val amount =
                      boxes(b).getRegisters().get(1).getValue().asInstanceOf[Long]
                    val userAddress = Address
                      .fromPropositionBytes(
                        cte.ctx.getNetworkType(),
                        boxes(b)
                          .getRegisters()
                          .get(0)
                          .getValue()
                          .asInstanceOf[Coll[Byte]]
                          .toArray
                      )
                    val st = StakeTransaction(
                      cte.ctx,
                      amount,
                      userAddress,
                      Address.create(Env.operatorAddress),
                      contractSignature.daoKey
                    )
                    val proxyContextVars = List(
                      ContextVar.of(
                        0.toByte,
                        Paideia
                          .getConfig(contractSignature.daoKey)
                          .getProof(
                            ConfKeys.im_paideia_staking_state_tokenid,
                            ConfKeys.im_paideia_dao_name
                          )(
                            Some(
                              ADDigest @@ st
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
                        st.stakingContextVars.companionContextVars(0).getValue()
                      ),
                      ContextVar.of(
                        2.toByte,
                        st.stakingContextVars.companionContextVars(1).getValue()
                      )
                    )
                    st.userInputs = List(boxes(b).withContextVars(proxyContextVars: _*))
                    st
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
    cons.put("_IM_PAIDEIA_DAO_NAME", ConfKeys.im_paideia_dao_name.ergoValue.getValue())
    cons.put(
      "_STAKE_KEY",
      Colls.fromArray(" Membership".getBytes(StandardCharsets.UTF_8))
    )
    cons.put(
      "_POWERED_BY_PAIDEIA",
      Colls.fromArray(
        " - Powered by Paideia - https://app.paideia.im".getBytes(StandardCharsets.UTF_8)
      )
    )
    cons
  }
}

object StakeProxy extends PaideiaActor {
  override def apply(
    configKey: DAOConfigKey,
    daoKey: String,
    digest: Option[ADDigest] = None
  ): StakeProxy =
    contractFromConfig(configKey, daoKey, digest)

  override def apply(contractSignature: PaideiaContractSignature): StakeProxy =
    getContractInstance[StakeProxy](contractSignature, new StakeProxy(contractSignature))
}
