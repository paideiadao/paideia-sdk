package im.paideia.staking.contracts

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.staking.TotalStakingState
import org.ergoplatform.sdk.ErgoToken
import im.paideia.staking.boxes.StakeStateBox
import scala.collection.mutable.HashMap
import org.ergoplatform.sdk.ErgoId
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
import im.paideia.staking.StakingSnapshot
import org.ergoplatform.appkit.ErgoValue
import im.paideia.staking.StakingContextVars
import sigma.AvlTree
import sigma.Coll
import work.lithos.plasma.ByteConversion
import im.paideia.staking.StakeRecord
import im.paideia.common.events.CreateTransactionsEvent
import java.lang
import im.paideia.staking.ParticipationRecord
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import org.ergoplatform.appkit.InputBox
import im.paideia.util.TxTypes
import im.paideia.DAOConfigKey
import im.paideia.DAODefaultedException

class StakeState(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(
    contractSignature,
    longLivingKey = ConfKeys.im_paideia_contracts_staking_state.originalKey
  ) {

  def box(
    ctx: BlockchainContextImpl,
    daoKey: String,
    value: Long,
    stakedTokenTotal: Long,
    nextEmission: Long,
    profit: Array[Long],
    snapshotProfit: Array[Long],
    extraTokens: List[ErgoToken] = List[ErgoToken](),
    snapshots: Array[StakingSnapshot],
    voted: Long,
    votedTotal: Long
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
      snapshotProfit,
      state.currentStakingState.stakeRecords.digest,
      state.currentStakingState.participationRecords.digest,
      snapshots,
      voted,
      votedTotal
    )
  }

  def emptyBox(ctx: BlockchainContextImpl, dao: DAO, stakePoolSize: Long) = {
    val state = TotalStakingState(dao.key)

    val emptyProfit = new Array[Long](2).toList

    val emissionDelay = dao.config[Long](ConfKeys.im_paideia_staking_emission_delay)
    box(
      ctx,
      dao.key,
      1000000000L,
      stakePoolSize,
      state.currentStakingState.emissionTime,
      emptyProfit.toArray,
      emptyProfit.toArray,
      List[ErgoToken](),
      Range(0, emissionDelay.toInt)
        .map(i =>
          StakingSnapshot(
            0L,
            0L,
            0L,
            state.currentStakingState.stakeRecords.digest,
            state.currentStakingState.participationRecords.digest,
            0L,
            0L
          )
        )
        .toArray,
      0L,
      0L
    )
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      val b = StakeStateBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse =
      event match {
        case cte: CreateTransactionsEvent =>
          PaideiaEventResponse.merge(getUtxoSet.toList.map { b =>
            {
              (if (
                 TotalStakingState(
                   contractSignature.daoKey
                 ).snapshots.size >= Paideia
                   .getConfig(contractSignature.daoKey)[Long](
                     ConfKeys.im_paideia_staking_emission_delay
                   )
               ) {
                 val stakeStateInput = boxes(b)
                 val stakeBox = StakeStateBox.fromInputBox(cte.ctx, stakeStateInput)
                 if (
                   stakeBox.state
                     .firstMatchingSnapshot(
                       stakeBox.snapshots(0).stakeDigest,
                       stakeBox.snapshots(0).participationDigest
                     )
                     .size(Some(stakeBox.snapshots(0).stakeDigest)) > 0
                 ) {
                   PaideiaEventResponse(
                     1,
                     List(
                       CompoundTransaction(
                         cte.ctx,
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
               }) + (if (
                       cte.currentTime > StakeStateBox
                         .fromInputBox(cte.ctx, boxes(b))
                         .nextEmission
                     ) {
                       try {
                         PaideiaEventResponse(
                           1,
                           List(
                             EmitTransaction(
                               cte.ctx,
                               boxes(b),
                               Address.create(Env.operatorAddress).getErgoAddress,
                               contractSignature.daoKey
                             )
                           )
                         )
                       } catch {
                         case e: DAODefaultedException => PaideiaEventResponse(0)
                         case e: Exception             => throw e
                       }
                     } else {
                       PaideiaEventResponse(0)
                     })
            }
          })
        case te: TransactionEvent =>
          PaideiaEventResponse.merge(
            te.tx
              .getInputs()
              .asScala
              .map(eti =>
                if (getUtxoSet.contains(eti.getBoxId())) {
                  val stakingState =
                    StakeStateBox.fromInputBox(te.ctx, boxes(eti.getBoxId()))
                  val companionContext = te.tx
                    .getInputs()
                    .get(1)
                    .getSpendingProof()
                    .getExtension()
                    .asScala
                    .map((kv: (String, String)) =>
                      (kv._1.toByte, ErgoValue.fromHex(kv._2))
                    )
                    .toMap[Byte, ErgoValue[_]]
                  val context = eti
                    .getSpendingProof()
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
                  val participationDigestOrHeight =
                    if (te.mempool)
                      Left(
                        stakingState.participationDigest
                      )
                    else
                      Right(te.height)
                  context(1.toByte) match {
                    case TxTypes.STAKE =>
                      val operations =
                        companionContext(1.toByte)
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
                      stakingState.state.currentStakingState.stakeRecords
                        .insertWithDigest(operations: _*)(digestOrHeight)
                    case TxTypes.CHANGE_STAKE =>
                      val operations =
                        companionContext(1.toByte)
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
                      stakingState.state.currentStakingState.stakeRecords
                        .updateWithDigest(operations: _*)(digestOrHeight)
                    case TxTypes.UNSTAKE =>
                      val operations =
                        companionContext(1.toByte)
                          .getValue()
                          .asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]]
                          .toArray
                          .map((kv: (Coll[Byte], Coll[Byte])) =>
                            ByteConversion.convertsId.convertFromBytes(kv._1.toArray)
                          )
                      stakingState.state.currentStakingState.stakeRecords
                        .deleteWithDigest(operations: _*)(digestOrHeight)
                    case TxTypes.SNAPSHOT =>
                      if (
                        !stakingState.state.snapshots
                          .contains(stakingState.newNextEmission)
                      )
                        stakingState.state.snapshots(stakingState.newNextEmission) =
                          stakingState.state.currentStakingState
                            .clone(contractSignature.daoKey, stakingState.newNextEmission)
                      val currentParticipation =
                        stakingState.state.currentStakingState.participationRecords
                          .getMap(participationDigestOrHeight.left.toOption)
                          .get
                      val participationResult =
                        stakingState.state.currentStakingState.participationRecords
                          .deleteWithDigest(currentParticipation.toMap.keys.toArray: _*)(
                            participationDigestOrHeight
                          )
                    case TxTypes.COMPOUND =>
                      val operations =
                        companionContext(1.toByte)
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
                      stakingState.state.currentStakingState.stakeRecords
                        .updateWithDigest(operations: _*)(digestOrHeight)
                      stakingState.state
                        .firstMatchingSnapshot(
                          stakingState.snapshots(0).stakeDigest,
                          stakingState.snapshots(0).participationDigest
                        )
                        .stakeRecords
                        .deleteWithDigest(removeOps: _*)(
                          if (te.mempool)
                            Left(
                              stakingState.snapshots(0).stakeDigest
                            )
                          else
                            Right(te.height)
                        )
                    case TxTypes.VOTE =>
                      val stakeKey =
                        te.tx.getOutputs().get(3).getAssets().get(0).getTokenId()
                      val currentParticipation = stakingState.state.currentStakingState
                        .getParticipations(
                          List(stakeKey),
                          participationDigestOrHeight.left.toOption
                        )
                        .response(0)
                        .tryOp
                        .get
                      val operations = Array(
                        (
                          ErgoId.create(stakeKey),
                          StakeRecord.stakeRecordConversion
                            .convertFromBytes(
                              companionContext(6.toByte)
                                .getValue()
                                .asInstanceOf[Coll[Byte]]
                                .toArray
                            )
                        )
                      )
                      val participationOperations = Array(
                        (
                          ErgoId.create(stakeKey),
                          ParticipationRecord.participationRecordConversion
                            .convertFromBytes(
                              companionContext(7.toByte)
                                .getValue()
                                .asInstanceOf[Coll[Byte]]
                                .toArray
                            )
                        )
                      )
                      stakingState.state.currentStakingState.stakeRecords
                        .updateWithDigest(operations: _*)(digestOrHeight)
                      if (currentParticipation.isDefined)
                        stakingState.state.currentStakingState.participationRecords
                          .updateWithDigest(participationOperations: _*)(
                            participationDigestOrHeight
                          )
                      else
                        stakingState.state.currentStakingState.participationRecords
                          .insertWithDigest(participationOperations: _*)(
                            participationDigestOrHeight
                          )
                    case TxTypes.PROFIT_SHARE =>
                    case _                    => ???
                  }
                  PaideiaEventResponse(2)
                } else {
                  PaideiaEventResponse(0)
                }
              )
          )
        case _ => PaideiaEventResponse(0)
      }
    PaideiaEventResponse.merge(List(super.handleEvent(event), response))
  }

  def getConfigContext(configDigest: Option[ADDigest], companionKey: DAOConfigKey) = {
    Paideia
      .getConfig(contractSignature.daoKey)
      .getProof(
        ConfKeys.im_paideia_contracts_staking_state,
        companionKey
      )(configDigest)
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
      "_IM_PAIDEIA_CONTRACTS_STAKING_STATE",
      ConfKeys.im_paideia_contracts_staking_state.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_STAKE",
      ConfKeys.im_paideia_contracts_staking_stake.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_CHANGESTAKE",
      ConfKeys.im_paideia_contracts_staking_changestake.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE",
      ConfKeys.im_paideia_contracts_staking_unstake.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT",
      ConfKeys.im_paideia_contracts_staking_snapshot.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND",
      ConfKeys.im_paideia_contracts_staking_compound.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_PROFITSHARE",
      ConfKeys.im_paideia_contracts_staking_profitshare.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_VOTE",
      ConfKeys.im_paideia_contracts_staking_vote.ergoValue.getValue()
    )
    cons
  }
}

object StakeState extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): StakeState =
    getContractInstance[StakeState](
      contractSignature,
      new StakeState(contractSignature)
    )
}
