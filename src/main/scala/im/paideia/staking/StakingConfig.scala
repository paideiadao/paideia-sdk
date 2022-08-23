package im.paideia.staking

import im.paideia.util.Util

case class StakingConfig(
    nftId: String,
    stakedTokenId: String,
    emissionAmount: Long,
    emissionDelay: Long,
    cycleLength: Long
)

object StakingConfig {
    def test: StakingConfig = StakingConfig(
        Util.randomKey,
        Util.randomKey,
        100000L,
        4L,
        3600000L
    )
}
