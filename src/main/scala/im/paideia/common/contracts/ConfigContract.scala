package im.paideia.common.contracts

import org.ergoplatform.appkit.NetworkType

case class Config(version: String = "latest", constants: Map[String,Object] = Map[String,Object](), networkType: NetworkType) extends PaideiaContract
