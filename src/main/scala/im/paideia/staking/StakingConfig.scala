package im.paideia.staking

import im.paideia.util.Util

case class StakingConfig(
    nftId: String,
    stakedTokenId: String,
    emissionAmount: Long,
    emissionDelay: Long,
    cycleLength: Long,
    profitTokens: List[(String,Long)]
)

object StakingConfig {
    def test: StakingConfig = {
        val nftId = Util.randomKey
        val stakedTokenId = Util.randomKey
        StakingConfig(
            nftId,
            stakedTokenId,
            100000L,
            4L,
            3600000L,
            List[(String,Long)]((stakedTokenId,1000L),(nftId,1000L),(Util.randomKey,1000L))
        )
    }
}
