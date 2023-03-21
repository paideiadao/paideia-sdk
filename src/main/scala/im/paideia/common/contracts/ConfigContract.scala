package im.paideia.common.contracts

import im.paideia.DAO
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.common.PaideiaEvent
import im.paideia.common.PaideiaEventResponse
import im.paideia.common.TransactionEvent
import im.paideia.common.boxes.ConfigBox
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.restapi.client.ErgoTransactionInput

import java.util.HashMap
import scala.collection.JavaConverters._
import scorex.crypto.authds.ADDigest

/**
  * This class represents a configuration contract and extends the PaideiaContract abstract class.
  *
  * @param contractSignature The PaideiaContractSignature object that encapsulates the keys necessary to access this contract.
  */
class Config(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  /**
    * Creates a ConfigBox object given a BlockchainContextImpl object and a DAO object.
    *
    * @param ctx The BlockchainContextImpl to use when creating the ConfigBox.
    * @param dao The DAO to use when creating the ConfigBox.
    * @return A ConfigBox object.
    */
  def box(
    ctx: BlockchainContextImpl,
    dao: DAO,
    digestOpt: Option[ADDigest] = None
  ): ConfigBox = {
    val res = new ConfigBox(dao.config, digestOpt)
    res.ctx      = ctx
    res.contract = contract
    res.value    = 1000000L
    res.tokens   = List(new ErgoToken(dao.key, 1L))
    res
  }

  /**
    * Handles events related to the configuration contract, primarily TransactionEvents.
    *
    * @param event The PaideiaEvent to handle.
    * @return A PaideiaEventResponse object, either empty or with a response code if the event was successfully handled.
    */
  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case te: TransactionEvent => {
        val utxoSet = if (te.mempool) getUtxoSet else utxos
        PaideiaEventResponse.merge(
          te.tx
            .getInputs()
            .asScala
            .map { (eti: ErgoTransactionInput) =>
              {
                if (utxoSet.contains(eti.getBoxId())) {
                  Paideia
                    ._daoMap(boxes(eti.getBoxId()).getTokens().get(0).getId().toString)
                    .config
                    .handleExtension(eti)
                  PaideiaEventResponse(2)
                } else {
                  PaideiaEventResponse(0)
                }
              }
            }
            .toList
        )
      }
      case _ => PaideiaEventResponse(0)
    }
    val superResponse = super.handleEvent(event)
    PaideiaEventResponse.merge(List(response, superResponse))
  }

  /**
    * Represents the constants defined in this contract. Returns a HashMap object with the following values:
    * "_IM_PAIDEIA_DAO_ACTION_TOKENID" - the im_paideia_dao_action_tokenid value from ConfKeys converted into an array of bytes.
    * "_IM_PAIDEIA_CONTRACTS_CONFIG" - the im_paideia_contracts_config ergo value converted into a String.
    *
    * @return A HashMap[String,Object] containing the constants defined in this contract.
    */
  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_DAO_ACTION_TOKENID",
      Paideia
        .getConfig(contractSignature.daoKey)
        .getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid)
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_CONFIG",
      ConfKeys.im_paideia_contracts_config.ergoValue.getValue()
    )
    cons
  }
}

/**
  * This object represents a Paideia contract configuration.
  */
object Config extends PaideiaActor {

  /**
    * Instantiates and returns a new instance of Config.
    * @param contractSignature The signature of the Paideia contract.
    * @return The newly instantiated Config object.
    */
  override def apply(contractSignature: PaideiaContractSignature): Config =
    getContractInstance[Config](contractSignature, new Config(contractSignature))
}
