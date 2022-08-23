package im.paideia.governance

import im.paideia.util.Util

case class DAOConfig(
    val configTokenId: String
)

object DAOConfig {
    def test: DAOConfig = 
        new DAOConfig(
            configTokenId = Util.randomKey
    )
}
