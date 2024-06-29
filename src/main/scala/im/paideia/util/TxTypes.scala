package im.paideia.util

import org.ergoplatform.appkit.ErgoValue

object TxTypes {
  val STAKE          = ErgoValue.of(0.toByte)
  val CHANGE_STAKE   = ErgoValue.of(1.toByte)
  val UNSTAKE        = ErgoValue.of(2.toByte)
  val SNAPSHOT       = ErgoValue.of(3.toByte)
  val COMPOUND       = ErgoValue.of(4.toByte)
  val PROFIT_SHARE   = ErgoValue.of(5.toByte)
  val VOTE           = ErgoValue.of(6.toByte)
  val UPDATE         = ErgoValue.of(7.toByte)
  val CHANGE_CONFIG  = ErgoValue.of(8.toByte)
  val TREASURY_SPEND = ErgoValue.of(9.toByte)
  val CONSOLIDATE    = ErgoValue.of(10.toByte)
}
