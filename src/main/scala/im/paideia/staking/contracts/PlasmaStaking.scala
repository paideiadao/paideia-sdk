package im.paideia.staking.contracts

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts.PaideiaContract

case class PlasmaStaking(version: String = "latest", constants: Map[String,Object] = Map[String,Object](), networkType: NetworkType) extends PaideiaContract 

