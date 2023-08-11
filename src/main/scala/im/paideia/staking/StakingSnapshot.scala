package im.paideia.staking

import scorex.crypto.authds.ADDigest

final case class StakingSnapshot(
  totalStaked: Long,
  voted: Long,
  votedTotal: Long,
  stakeDigest: ADDigest,
  participationDigest: ADDigest,
  pureParticipationWeight: Long,
  participationWeight: Long
)
