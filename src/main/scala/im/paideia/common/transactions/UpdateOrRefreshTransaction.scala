package im.paideia.common.transactions

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.Address
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.ContextVar
import im.paideia.util.TxTypes
import org.ergoplatform.appkit.ErgoValue
import sigma.Coll
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.common.filtering.CompareField
import im.paideia.common.boxes.ConfigBox

final case class UpdateOrRefreshTransaction(
  _ctx: BlockchainContextImpl,
  outdatedBoxes: List[InputBox],
  longLivingKey: String,
  dao: DAO,
  newAddress: Address,
  operatorAddress: Address
) extends PaideiaTransaction {
  ctx = _ctx

  val configInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      dao.key,
      CompareField.ASSET,
      0
    )
  )(0)

  val configInputBox = ConfigBox.fromInputBox(ctx, configInput)

  minimizeChangeBox = false
  val updatedBoxes = outdatedBoxes.map(outdatedBox =>
    _ctx
      .newTxBuilder()
      .outBoxBuilder()
      .contract(newAddress.toErgoContract())
      .value(outdatedBox.getValue() - 2000000L / outdatedBoxes.length)
      .tokens(outdatedBox.getTokens().asScala: _*)
      .registers(outdatedBox.getRegisters().asScala: _*)
      .build()
  )

  ctx = _ctx
  fee = 1000000L
  inputs = outdatedBoxes.map(ib =>
    ib.withContextVars(
      ContextVar.of(0.toByte, TxTypes.UPDATE),
      ContextVar.of(
        1.toByte,
        dao.config.getProof(longLivingKey)(configInputBox.digestOpt)
      )
    )
  )
  dataInputs    = List(configInput)
  outputs       = updatedBoxes
  changeAddress = operatorAddress
}
