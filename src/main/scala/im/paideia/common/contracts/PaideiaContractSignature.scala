package im.paideia.common.contracts

import org.ergoplatform.appkit.NetworkType

case class PaideiaContractSignature(
    className: String = "",
    version: String = "latest",
    networkType: NetworkType = NetworkType.MAINNET,
    contractHash: List[Byte] = List(0.toByte),
    wrappedContract: String = ""
)
