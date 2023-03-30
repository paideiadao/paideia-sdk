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
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.TransactionEvent
import im.paideia.common.events.BlockEvent
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
import org.ergoplatform.appkit.ErgoValue
import im.paideia.staking.StakingContextVars
import special.sigma.AvlTree
import special.collection.Coll
import io.getblok.getblok_plasma.ByteConversion
import im.paideia.staking.StakeRecord

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
    val response: PaideiaEventResponse =
      try {
        event match {
          case te: TransactionEvent => {
            val boxSet = if (te.mempool) getUtxoSet else utxos
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
                        ).snapshots.size >= Paideia
                          .getConfig(contractSignature.daoKey)[Long](
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
                .toList ++ te.tx
                .getInputs()
                .asScala
                .map(eti =>
                  if (boxSet.contains(eti.getBoxId())) {
                    val stakingState =
                      StakeStateBox.fromInputBox(te.ctx, boxes(eti.getBoxId()))
                    val context = eti
                      .getExtension()
                      .asScala
                      .map((kv: (String, String)) =>
                        (kv._1.toByte, ErgoValue.fromHex(kv._2))
                      )
                      .toMap[Byte, ErgoValue[_]]
                    val digestOrHeight =
                      if (te.mempool)
                        Left(
                          stakingState.stateDigest
                        )
                      else
                        Right(te.height)
                    context(1.toByte) match {
                      case StakingContextVars.STAKE =>
                        val operations =
                          context(2.toByte)
                            .getValue()
                            .asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]]
                            .toArray
                            .map((kv: (Coll[Byte], Coll[Byte])) =>
                              (
                                ByteConversion.convertsId.convertFromBytes(kv._1.toArray),
                                StakeRecord.stakeRecordConversion
                                  .convertFromBytes(kv._2.toArray)
                              )
                            )
                        stakingState.state.currentStakingState.plasmaMap
                          .insertWithDigest(operations: _*)(digestOrHeight)
                      case StakingContextVars.CHANGE_STAKE =>
                        val operations =
                          context(2.toByte)
                            .getValue()
                            .asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]]
                            .toArray
                            .map((kv: (Coll[Byte], Coll[Byte])) =>
                              (
                                ByteConversion.convertsId.convertFromBytes(kv._1.toArray),
                                StakeRecord.stakeRecordConversion
                                  .convertFromBytes(kv._2.toArray)
                              )
                            )
                        stakingState.state.currentStakingState.plasmaMap
                          .updateWithDigest(operations: _*)(digestOrHeight)
                      case StakingContextVars.UNSTAKE =>
                        val operations =
                          context(2.toByte)
                            .getValue()
                            .asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]]
                            .toArray
                            .map((kv: (Coll[Byte], Coll[Byte])) =>
                              ByteConversion.convertsId.convertFromBytes(kv._1.toArray)
                            )
                        stakingState.state.currentStakingState.plasmaMap
                          .deleteWithDigest(operations: _*)(digestOrHeight)
                      case StakingContextVars.SNAPSHOT =>
                        if (!stakingState.state.snapshots
                              .contains(stakingState.newNextEmission))
                          stakingState.state.snapshots(stakingState.newNextEmission) =
                            stakingState.state.currentStakingState
                              .clone(
                                contractSignature.daoKey,
                                stakingState.newNextEmission
                              )
                      case StakingContextVars.COMPOUND =>
                        val operations =
                          context(2.toByte)
                            .getValue()
                            .asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]]
                            .toArray
                            .map((kv: (Coll[Byte], Coll[Byte])) =>
                              (
                                ByteConversion.convertsId.convertFromBytes(kv._1.toArray),
                                StakeRecord.stakeRecordConversion
                                  .convertFromBytes(kv._2.toArray)
                              )
                            )
                        val removeOps = operations.map(_._1)
                        stakingState.state.currentStakingState.plasmaMap
                          .updateWithDigest(operations: _*)(digestOrHeight)
                        stakingState.state
                          .firstMatchingSnapshot(stakingState.snapshots(0).digest)
                          .plasmaMap
                          .deleteWithDigest(removeOps: _*)(
                            if (te.mempool)
                              Left(
                                stakingState.snapshots(0).digest
                              )
                            else
                              Right(te.height)
                          )
                      case StakingContextVars.PROFIT_SHARE =>
                      case _                               => ???
                    }
                    PaideiaEventResponse(2)
                  } else {
                    PaideiaEventResponse(0)
                  }
                )
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
      } catch {
        case e: Exception => PaideiaEventResponse(0)
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
