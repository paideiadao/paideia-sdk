package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContract
import org.ergoplatform.appkit.NetworkType

case class ProtoDAO(version: String = "latest", constants: Map[String,Object] = Map[String,Object](), networkType: NetworkType) extends PaideiaContract
