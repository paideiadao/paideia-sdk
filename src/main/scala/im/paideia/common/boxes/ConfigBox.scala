package im.paideia.common.boxes

import im.paideia.DAOConfig
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContract
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import scorex.crypto.authds.ADDigest
import org.ergoplatform.sdk.ErgoToken
import im.paideia.DAO
import org.ergoplatform.appkit.InputBox
import scorex.crypto.hash.Blake2b256
import sigma.Coll
import sigma.AvlTree
import org.ergoplatform.appkit.ErgoContract
import im.paideia.Paideia

/** This class represents a configuration blockchain transaction output.
  * @param config
  *   A DAOConfig object representing the configuration for this output.
  */
final case class ConfigBox(
  _ctx: BlockchainContextImpl,
  dao: DAO,
  useContract: Config,
  digestOpt: Option[ADDigest] = None,
  _value: Long                = 1000000000L
) extends PaideiaBox {
  ctx      = _ctx
  contract = useContract.contract
  value    = _value
  override def tokens: List[ErgoToken] = {
    List(
      new ErgoToken(dao.key, 1L)
    )
  }

  /** Gets the registers for the output.
    * @return
    *   A list containing the single register value which stores the configuration value.
    */
  override def registers: List[ErgoValue[_]] = {
    List(dao.config._config.ergoValue(digestOpt))
  }
}

object ConfigBox {

  /** Parse from existing input box to ActionSendFundsBasicBox instance
    * @param ctx
    *   The context of blockchain related values.
    * @param inp
    *   An existing box
    * @return
    *   ActionSendFundsBasicBox instance
    */
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ConfigBox = {
    val contract = Config
      .contractInstances(Blake2b256(inp.getErgoTree().bytes).array.toList)
      .asInstanceOf[Config]
    val daoKey = inp.getTokens().get(0).id.toString()
    if (daoKey != contract.contractSignature.daoKey)
      throw new Exception("Daokey did not match token present")
    val configTreeDigest = ADDigest @@ inp
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray
    ConfigBox(
      ctx,
      Paideia.getDAO(daoKey),
      contract,
      Some(configTreeDigest),
      inp.getValue()
    )
  }
}
