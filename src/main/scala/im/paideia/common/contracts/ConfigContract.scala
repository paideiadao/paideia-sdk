package im.paideia.common.contracts

import im.paideia.DAO
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import im.paideia.common.boxes.ConfigBox
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoToken
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.restapi.client.ErgoTransactionInput

import scala.collection.mutable.HashMap
import scala.collection.JavaConverters._
import scorex.crypto.authds.ADDigest
import im.paideia.common.events.UpdateConfigEvent
import im.paideia.common.transactions.PaideiaTransaction
import sigma.ast.ConstantPlaceholder
import sigma.ast.SCollection
import sigma.ast.SByte
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import sigma.Colls
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfigKey
import im.paideia.governance.boxes.ActionUpdateConfigBox
import im.paideia.governance.contracts.ActionUpdateConfig
import sigma.AvlTree

/** This class represents a configuration contract and extends the PaideiaContract
  * abstract class.
  *
  * @param contractSignature
  *   The PaideiaContractSignature object that encapsulates the keys necessary to access
  *   this contract.
  */
class Config(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(
    contractSignature,
    ConfKeys.im_paideia_contracts_config.originalKey
  ) {

  /** Creates a ConfigBox object given a BlockchainContextImpl object and a DAO object.
    *
    * @param ctx
    *   The BlockchainContextImpl to use when creating the ConfigBox.
    * @param dao
    *   The DAO to use when creating the ConfigBox.
    * @return
    *   A ConfigBox object.
    */
  def box(
    ctx: BlockchainContextImpl,
    dao: DAO,
    digestOpt: Option[ADDigest] = None
  ): ConfigBox = {
    new ConfigBox(ctx, dao, this, digestOpt)
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    try {
      val b = ConfigBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
  }

  /** Represents the constants defined in this contract. Returns a HashMap object with the
    * following values: "_IM_PAIDEIA_DAO_ACTION_TOKENID" - the
    * im_paideia_dao_action_tokenid value from ConfKeys converted into an array of bytes.
    * "_IM_PAIDEIA_CONTRACTS_CONFIG" - the im_paideia_contracts_config ergo value
    * converted into a String.
    *
    * @return
    *   A HashMap[String,Object] containing the constants defined in this contract.
    */
  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_CONFIG",
      ConfKeys.im_paideia_contracts_config.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_ACTION",
      Colls.fromArray(
        ConfKeys.im_paideia_contracts_action(Array[Byte]()).originalKeyBytes
      )
    )
    cons
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case te: TransactionEvent =>
        if (te.tx.getInputs().size() > 1)
          if (boxes.contains(te.tx.getInputs().get(0).getBoxId())) {
            val actionBox = ActionUpdateConfigBox.fromInputBox(
              te.ctx,
              Paideia.getBoxById(te.tx.getInputs().get(1).getBoxId()).get
            )
            Paideia
              .getConfig(contractSignature.daoKey)
              .handleUpdateEvent(
                UpdateConfigEvent(
                  te.ctx,
                  contractSignature.daoKey,
                  if (te.mempool)
                    Left(
                      ADDigest @@ boxes(te.tx.getInputs().get(0).getBoxId())
                        .getRegisters()
                        .get(0)
                        .getValue()
                        .asInstanceOf[AvlTree]
                        .digest
                        .toArray
                    )
                  else
                    Right(te.height),
                  actionBox.remove.toArray,
                  actionBox.update.toArray,
                  actionBox.insert.toArray
                )
              )
            PaideiaEventResponse(2, List[PaideiaTransaction]())
          } else PaideiaEventResponse(0)
        else PaideiaEventResponse(0)
      case _ => PaideiaEventResponse(0)
    }
    PaideiaEventResponse.merge(List(super.handleEvent(event), response))
  }

  override lazy val parameters: Map[String, Constant[SType]] = {
    val params = new scala.collection.mutable.HashMap[String, Constant[SType]]()
    params.put(
      "imPaideiaDaoActionTokenId",
      ByteArrayConstant(
        Colls.fromArray(
          Paideia
            .getConfig(contractSignature.daoKey)
            .getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid)
        )
      )
    )
    params.toMap
  }
}

/** This object represents a Paideia contract configuration.
  */
object Config extends PaideiaActor {
  override def apply(
    configKey: DAOConfigKey,
    daoKey: String,
    digest: Option[ADDigest] = None
  ): Config =
    contractFromConfig[Config](configKey, daoKey, digest)

  /** Instantiates and returns a new instance of Config.
    * @param contractSignature
    *   The signature of the Paideia contract.
    * @return
    *   The newly instantiated Config object.
    */
  override def apply(contractSignature: PaideiaContractSignature): Config =
    getContractInstance[Config](contractSignature, new Config(contractSignature))
}
