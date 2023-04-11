package im.paideia.staking

import scorex.crypto.authds.ADDigest

final case class StakingSnapshot(
  totalStaked: Long,
  digest: ADDigest,
  profit: List[Long]
)
