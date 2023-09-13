package im.paideia.common.transactions

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.Address
import scala.collection.JavaConverters._

final case class RefundTransaction(
  _ctx: BlockchainContextImpl,
  refundBox: InputBox,
  refundAddress: Address
) extends PaideiaTransaction {

  val refundedBox = _ctx
    .newTxBuilder()
    .outBoxBuilder()
    .contract(refundAddress.toErgoContract())
    .value(refundBox.getValue() - 1000000L)
    .tokens(refundBox.getTokens().asScala: _*)
    .build()
  ctx           = _ctx
  fee           = 1000000L
  inputs        = List(refundBox)
  outputs       = List(refundedBox)
  changeAddress = refundAddress
}
