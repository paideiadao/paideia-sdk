package im.paideia.common.events

import scorex.crypto.authds.ADDigest
import im.paideia.DAOConfigKey
import org.ergoplatform.appkit.impl.BlockchainContextImpl

final case class UpdateConfigEvent(
  ctx: BlockchainContextImpl,
  daoKey: String,
  digestOrHeight: Either[ADDigest, Int],
  removedKeys: Array[DAOConfigKey],
  updatedEntries: Array[(DAOConfigKey, Array[Byte])],
  insertEntries: Array[(DAOConfigKey, Array[Byte])]
) extends PaideiaEvent(ctx)
