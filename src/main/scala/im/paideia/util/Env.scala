package im.paideia.util

import com.typesafe.config.ConfigFactory

object Env {
    val conf = ConfigFactory.load().getConfig("paideia")
    def daoTokenId: String = conf.getString("daoTokenId")
    def configTokenId: String = conf.getString("configTokenId")
    def paideiaTokenId: String = conf.getString("paideiaTokenId")
}
