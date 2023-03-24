package im.paideia.staking

import scorex.crypto.authds.ADDigest

final case class StakeSnapshot(
  totalStaked: Long,
  digest: ADDigest,
  profit: List[Long]
)
