package im.paideia.util

import com.typesafe.config.ConfigFactory
import org.ergoplatform.appkit.NetworkType

object Env {
    val conf = ConfigFactory.load().getConfig("paideia")
    def daoTokenId: String = conf.getString("daoTokenId")
    def configTokenId: String = conf.getString("configTokenId")
    def paideiaTokenId: String = conf.getString("paideiaTokenId")
    def networkType: NetworkType = NetworkType.fromValue(conf.getString("networkType"))
    def paideiaDaoKey: String = conf.getString("paideiaDaoKey")
    def paideiaOriginNFT: String = conf.getString("paideiaOriginNFT")
    def operatorAddress: String = conf.getString("operatorAddress")
    def compoundBatchSize: Int = conf.getInt("compoundBatchSize")
}
