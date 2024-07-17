package im.paideia.common.transactions

import im.paideia.util.TxTypes
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.Address
import sigma.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder

final case class ConsolidateTransaction(
  _ctx: BlockchainContextImpl,
  treasuryBoxes: List[InputBox]
) extends PaideiaTransaction {
  ctx               = _ctx
  minimizeChangeBox = false
  fee               = 2000000L
  inputs = treasuryBoxes.map(tb =>
    tb.withContextVars(
      ContextVar.of(0.toByte, TxTypes.CONSOLIDATE)
    )
  )
  outputs = List()
  changeAddress =
    Address.fromErgoTree(treasuryBoxes(0).getErgoTree(), ctx.getNetworkType())
}
