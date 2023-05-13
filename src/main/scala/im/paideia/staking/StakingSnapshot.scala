package im.paideia.staking

import scorex.crypto.authds.ADDigest

final case class StakingSnapshot(
  totalStaked: Long,
  voted: Long,
  votedTotal: Long,
  stakeDigest: ADDigest,
  participationDigest: ADDigest,
  profit: List[Long]
) {
  def addProfit(addedProfit: Array[Long]) = {
    StakingSnapshot(
      totalStaked,
      voted,
      votedTotal,
      stakeDigest,
      participationDigest,
      profit.zip(addedProfit).map(pp => pp._1 + pp._2)
    )
  }
}
