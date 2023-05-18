package im.paideia.common.events

import org.ergoplatform.appkit.impl.BlockchainContextImpl

/** A class representing an event in the Paideia contract
  *
  * @param _ctx
  *   The blockchain context where the event occurred
  */
class PaideiaEvent(val _ctx: BlockchainContextImpl)
