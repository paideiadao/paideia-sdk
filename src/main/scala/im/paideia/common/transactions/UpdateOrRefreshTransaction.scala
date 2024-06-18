package im.paideia.common.transactions

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.Address
import scala.collection.JavaConverters._

final case class UpdateOrRefreshTransaction(
  _ctx: BlockchainContextImpl,
  outdatedBox: InputBox,
  newAddress: Address,
  operatorAddress: Address
) extends PaideiaTransaction {
  minimizeChangeBox = false
  val updatedBox = _ctx
    .newTxBuilder()
    .outBoxBuilder()
    .contract(newAddress.toErgoContract())
    .value(outdatedBox.getValue() - 2000000L)
    .tokens(outdatedBox.getTokens().asScala: _*)
    .registers(outdatedBox.getRegisters().asScala: _*)
    .build()
  ctx           = _ctx
  fee           = 1000000L
  inputs        = List(outdatedBox)
  outputs       = List(updatedBox)
  changeAddress = operatorAddress
}
