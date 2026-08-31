package im.paideia.util

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.ergoplatform.appkit.NetworkType
import im.paideia.Paideia

/** A session's network/token/fee configuration (the `paideia` sub-config of whatever
  * application.conf was loaded), wrapped so each `PaideiaSession` can carry its own
  * rather than sharing one process-global `com.typesafe.config.Config`.
  */
class PaideiaEnv(val conf: Config) {
  def daoTokenId: String       = conf.getString("daoTokenId")
  def configTokenId: String    = conf.getString("configTokenId")
  def paideiaTokenId: String   = conf.getString("paideiaTokenId")
  def networkType: NetworkType = NetworkType.fromValue(conf.getString("networkType"))
  def paideiaDaoKey: String    = conf.getString("paideiaDaoKey")
  def paideiaOriginNFT: String = conf.getString("paideiaOriginNFT")
  def operatorAddress: String  = conf.getString("operatorAddress")
  def compoundBatchSize: Int   = conf.getInt("compoundBatchSize")
  def defaultBotFee: Long      = conf.getLong("defaultBotFee")
}

object PaideiaEnv {
  def load(): PaideiaEnv = new PaideiaEnv(ConfigFactory.load().getConfig("paideia"))
}

/** Facade over `Paideia.current.env`, kept so every existing caller (paideia-state
  * included) compiles and behaves identically without source changes. `conf` used to be
  * a `val`, loaded once at class-init time; it's a `def` now since it has to resolve
  * through whichever session is current at call time.
  */
object Env {
  def conf: Config              = Paideia.current.env.conf
  def daoTokenId: String        = Paideia.current.env.daoTokenId
  def configTokenId: String     = Paideia.current.env.configTokenId
  def paideiaTokenId: String    = Paideia.current.env.paideiaTokenId
  def networkType: NetworkType  = Paideia.current.env.networkType
  def paideiaDaoKey: String     = Paideia.current.env.paideiaDaoKey
  def paideiaOriginNFT: String  = Paideia.current.env.paideiaOriginNFT
  def operatorAddress: String   = Paideia.current.env.operatorAddress
  def compoundBatchSize: Int    = Paideia.current.env.compoundBatchSize
  def defaultBotFee: Long       = Paideia.current.env.defaultBotFee
}
