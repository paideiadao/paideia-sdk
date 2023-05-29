package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import im.paideia.governance.boxes.ActionUpdateConfigBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfigKey
import im.paideia.Paideia
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.BlockEvent
import org.ergoplatform.appkit.InputBox
import special.collection.Coll
import java.util.HashMap
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.governance.transactions.UpdateConfigTransaction
import im.paideia.common.events.TransactionEvent
import scala.collection.JavaConverters._
import im.paideia.common.events.UpdateConfigEvent
import im.paideia.common.boxes.ConfigBox
import im.paideia.common.contracts.Config
import special.sigma.AvlTree
import scorex.crypto.authds.ADDigest
import im.paideia.common.transactions.PaideiaTransaction
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.common.filtering.CompareField

class ActionUpdateConfig(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    proposalId: Int,
    optionId: Int,
    activationTime: Long,
    remove: List[DAOConfigKey],
    update: List[(DAOConfigKey, Array[Byte])],
    insert: List[(DAOConfigKey, Array[Byte])]
  ): ActionUpdateConfigBox = {
    ActionUpdateConfigBox(
      ctx,
      this,
      Paideia.getDAO(contractSignature.daoKey),
      proposalId,
      optionId,
      activationTime,
      remove,
      update,
      insert
    )
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        PaideiaEventResponse.merge(
          getUtxoSet
            .map(boxes(_))
            .map((b: InputBox) => {
              val proposalInput = Paideia
                .getBox(
                  new FilterLeaf[String](
                    FilterType.FTEQ,
                    new ErgoId(
                      Paideia
                        .getConfig(contractSignature.daoKey)
                        .getArray(ConfKeys.im_paideia_dao_proposal_tokenid)
                    )
                      .toString(),
                    CompareField.ASSET,
                    0
                  )
                )
                .filter((box: InputBox) =>
                  box
                    .getRegisters()
                    .get(0)
                    .getValue()
                    .asInstanceOf[Coll[Int]](0) == b
                    .getRegisters()
                    .get(0)
                    .getValue()
                    .asInstanceOf[Coll[Long]](0)
                    .toInt
                )(0)
              if (
                proposalInput
                  .getRegisters()
                  .get(0)
                  .getValue()
                  .asInstanceOf[Coll[Int]](1) == b
                  .getRegisters()
                  .get(0)
                  .getValue()
                  .asInstanceOf[Coll[Long]](1)
                  .toInt &&
                cte.currentTime > b
                  .getRegisters()
                  .get(0)
                  .getValue()
                  .asInstanceOf[Coll[Long]](3)
              ) {
                PaideiaEventResponse(
                  1,
                  List(
                    UpdateConfigTransaction(
                      cte.ctx,
                      Paideia.getDAO(contractSignature.daoKey),
                      b
                    )
                  )
                )
              } else {
                PaideiaEventResponse(0)
              }
            })
            .toList
        )
      }
      case te: TransactionEvent =>
        val boxSet = if (te.mempool) getUtxoSet else utxos
        if (te.tx.getInputs().size() > 1)
          if (boxSet.contains(te.tx.getInputs().get(1).getBoxId())) {
            val actionBox = ActionUpdateConfigBox.fromInputBox(
              te.ctx,
              boxes(te.tx.getInputs().get(1).getBoxId())
            )
            val configInput = Config(
              PaideiaContractSignature(daoKey = contractSignature.daoKey)
            ).boxes(te.tx.getInputs().get(0).getBoxId())
            Paideia
              .getConfig(contractSignature.daoKey)
              .handleUpdateEvent(
                UpdateConfigEvent(
                  te.ctx,
                  contractSignature.daoKey,
                  if (te.mempool)
                    Left(
                      ADDigest @@ configInput
                        .getRegisters()
                        .get(0)
                        .getValue()
                        .asInstanceOf[AvlTree]
                        .digest
                        .toArray
                    )
                  else
                    Right(te.height),
                  actionBox.remove.toArray,
                  actionBox.update.toArray,
                  actionBox.insert.toArray
                )
              )
            PaideiaEventResponse(2, List[PaideiaTransaction]())
          } else PaideiaEventResponse(0)
        else PaideiaEventResponse(0)
      case _ => PaideiaEventResponse(0)
    }
    val superResponse = super.handleEvent(event)
    response
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSignature.daoKey).getBytes())
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_CONFIG",
      ConfKeys.im_paideia_contracts_config.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_PROPOSAL_TOKENID",
      Paideia
        .getConfig(contractSignature.daoKey)
        .getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid)
    )
    cons
  }
}

object ActionUpdateConfig extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): ActionUpdateConfig =
    getContractInstance[ActionUpdateConfig](
      contractSignature,
      new ActionUpdateConfig(contractSignature)
    )
}
