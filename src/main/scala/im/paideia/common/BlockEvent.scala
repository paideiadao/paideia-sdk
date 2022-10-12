package im.paideia.common

import org.ergoplatform.restapi.client.BlockTransactions
import org.ergoplatform.restapi.client.FullBlock

final case class BlockEvent(block: FullBlock) extends PaideiaEvent
