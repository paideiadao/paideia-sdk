package im.paideia.staking

case class StakingConfig(
    nftId: String,
    stakedTokenId: String,
    emissionAmount: Long,
    emissionDelay: Long,
    cycleLength: Long
)

object StakingConfig {
    def test(): StakingConfig = StakingConfig(
        "29d6c2d943d7f5a2800095dba6b6168f1602d355480218f4a8a8c6575245a907",
        "0cd8c9f416e5b1ca9f986a7f10a84191dfb85941619e49e53c0dc30ebf83324b",
        100000L,
        4L,
        3600000L
    )
}
