package im.paideia.common.events

import org.ergoplatform.appkit.impl.BlockchainContextImpl

final case class CreateTransactionsEvent(
  ctx: BlockchainContextImpl,
  currentTime: Long,
  height: Long
) extends PaideiaEvent(ctx)
