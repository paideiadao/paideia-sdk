package im.paideia.common

import org.ergoplatform.restapi.client.ErgoTransaction

final case class TransactionEvent(mempool: Boolean, tx: ErgoTransaction) extends PaideiaEvent
