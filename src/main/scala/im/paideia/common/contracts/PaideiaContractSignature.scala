package im.paideia.common.contracts

import org.ergoplatform.appkit.NetworkType

/** Holds the signature for a PaideiaContract class.
  *
  * @param className
  *   The full name of the contract class, including its package.
  * @param version
  *   The version of the contract. Defaults to "latest".
  * @param networkType
  *   The network on which this contract is deployed. Defaults to [[NetworkType.MAINNET]].
  * @param contractHash
  *   The unique hash of the contract bytecode.
  * @param daoKey
  *   A string containing the key used by a DAO that is associated with this contract. Can
  *   be an empty string.
  */
case class PaideiaContractSignature(
  className: String        = "",
  version: String          = "latest",
  networkType: NetworkType = NetworkType.MAINNET,
  contractHash: List[Byte] = List(0.toByte),
  daoKey: String           = ""
) {

  def withDaoKey(_daoKey: String): PaideiaContractSignature =
    PaideiaContractSignature(className, version, networkType, contractHash, _daoKey)

  override def toString() = {
    "PaideiaContractSignature(%s,%s,%s,%s)".format(
      className,
      version,
      networkType.toString(),
      contractHash.map("%02x" format _).mkString
    )
  }
}
