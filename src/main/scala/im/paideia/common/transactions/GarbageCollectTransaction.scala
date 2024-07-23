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
import org.ergoplatform.sdk.ErgoId

final case class GarbageCollectTransaction(
  _ctx: BlockchainContextImpl,
  garbageBox: InputBox,
  tokensIdsToBurn: Array[ErgoId],
  operatorAddress: Address
) extends PaideiaTransaction {
  ctx = _ctx

  minimizeChangeBox = false
  fee               = 1000000L
  inputs = List(
    garbageBox.withContextVars(
      ContextVar.of(0.toByte, TxTypes.GARBAGE_COLLECT)
    )
  )
  outputs = List()
  tokensToBurn = garbageBox
    .getTokens()
    .asScala
    .filter(t => tokensIdsToBurn.contains(t.getId))
    .toList
  changeAddress = operatorAddress
}
