package im.paideia.staking.contracts

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts._
import sigmastate.Values
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.ErgoToken
import im.paideia.staking.boxes.StakeStateBox
import java.util.HashMap
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.DAO
import im.paideia.common.PaideiaEventResponse
import im.paideia.common.PaideiaEvent
import im.paideia.common.TransactionEvent
import im.paideia.common.BlockEvent
import im.paideia.staking.transactions.EmitTransaction
import org.ergoplatform.appkit.Address
import im.paideia.util.Env
import im.paideia.staking.transactions.CompoundTransaction
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import scala.collection.JavaConverters._
import im.paideia.Paideia
import org.ergoplatform.appkit.impl.InputBoxImpl
import scorex.crypto.authds.ADDigest
import im.paideia.staking.StakeSnapshot

class PlasmaStaking(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    daoKey: String,
    value: Long,
    stakedTokenTotal: Long,
    nextEmission: Long,
    profit: Array[Long],
    extraTokens: List[ErgoToken] = List[ErgoToken](),
    snapshots: Array[StakeSnapshot]
  ): StakeStateBox = {
    val dao   = Paideia.getDAO(daoKey)
    val state = TotalStakingState(daoKey)
    StakeStateBox(
      ctx,
      this,
      state,
      value,
      extraTokens,
      dao,
      stakedTokenTotal,
      nextEmission,
      profit,
      state.currentStakingState.plasmaMap.digest,
      snapshots
    )
  }

  def emptyBox(ctx: BlockchainContextImpl, dao: DAO, stakePoolSize: Long) = {
    val state = TotalStakingState(dao.key)
    val whiteListedTokens =
      dao.config.getArray[Array[Object]](ConfKeys.im_paideia_staking_profit_tokenids)

    val emptyProfit = new Array[Long](whiteListedTokens.size + 2).toList

    val emissionDelay = dao.config[Long](ConfKeys.im_paideia_staking_emission_delay)
    box(
      ctx,
      dao.key,
      1000000L,
      stakePoolSize,
      state.currentStakingState.emissionTime,
      emptyProfit.toArray,
      List[ErgoToken](),
      Range(0, emissionDelay.toInt)
        .map(i =>
          StakeSnapshot(0L, state.currentStakingState.plasmaMap.digest, emptyProfit)
        )
        .toArray
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
                val etotree  = eto.getErgoTree()
                val ergotree = ergoTree.bytesHex
                if (eto.getErgoTree() == ergoTree.bytesHex && TotalStakingState(
                      contractSignature.daoKey
                    ).snapshots.size >= Paideia.getConfig(contractSignature.daoKey)[Long](
                      ConfKeys.im_paideia_staking_emission_delay
                    )) {
                  val stakeStateInput = new InputBoxImpl(eto)
                  val stakeBox        = StakeStateBox.fromInputBox(te.ctx, stakeStateInput)
                  if (stakeBox.state
                        .firstMatchingSnapshot(stakeBox.snapshots(0).digest)
                        .size(Some(stakeBox.snapshots(0).digest)) > 0) {
                    PaideiaEventResponse(
                      1,
                      List(
                        CompoundTransaction(
                          te.ctx,
                          stakeStateInput,
                          Address.create(Env.operatorAddress).getErgoAddress,
                          contractSignature.daoKey
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
            }
            .toList
        )
      }
      case be: BlockEvent => {
        val stakeBox = boxes(getUtxoSet.toList(0))
        if (be.block.getHeader().getTimestamp() > StakeStateBox
              .fromInputBox(be._ctx, stakeBox)
              .nextEmission) {
          PaideiaEventResponse(
            1,
            List(
              EmitTransaction(
                be._ctx,
                stakeBox,
                Address.create(Env.operatorAddress).getErgoAddress,
                contractSignature.daoKey
              )
            )
          )
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
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSignature.daoKey).getBytes())
    cons.put(
      "_IM_PAIDEIA_STAKING_EMISSION_AMOUNT",
      ConfKeys.im_paideia_staking_emission_amount.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_EMISSION_DELAY",
      ConfKeys.im_paideia_staking_emission_delay.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_CYCLELENGTH",
      ConfKeys.im_paideia_staking_cyclelength.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_PROFIT_TOKENIDS",
      ConfKeys.im_paideia_staking_profit_tokenids.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_PROFIT_THRESHOLDS",
      ConfKeys.im_paideia_staking_profit_thresholds.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING",
      ConfKeys.im_paideia_contracts_staking.ergoValue.getValue()
    )
    cons
  }
}

object PlasmaStaking extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): PlasmaStaking =
    getContractInstance[PlasmaStaking](
      contractSignature,
      new PlasmaStaking(contractSignature)
    )
}
