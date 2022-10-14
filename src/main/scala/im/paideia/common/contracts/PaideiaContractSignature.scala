package im.paideia.common.contracts

import org.ergoplatform.appkit.NetworkType

case class PaideiaContractSignature(
    className: String = "",
    version: String = "latest",
    networkType: NetworkType = NetworkType.TESTNET,
    contractHash: Array[Byte] = Array(0.toByte),
    wrappedContract: String = ""
)
