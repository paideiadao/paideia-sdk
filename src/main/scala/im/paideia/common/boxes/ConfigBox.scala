package im.paideia.common.boxes

import im.paideia.DAOConfig
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContract
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import scorex.crypto.authds.ADDigest

/** This class represents a configuration blockchain transaction output.
  * @param config
  *   A DAOConfig object representing the configuration for this output.
  */
class ConfigBox(config: DAOConfig, digestOpt: Option[ADDigest] = None)
  extends PaideiaBox {

  /** Gets the registers for the output.
    * @return
    *   A list containing the single register value which stores the configuration value.
    */
  override def registers: List[ErgoValue[_]] = {
    List(config._config.ergoValue(digestOpt))
  }
}
