package im.paideia.common.contracts

import org.ergoplatform.appkit.ErgoToken
import im.paideia.common.boxes.TreasuryBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import java.util.HashMap
import im.paideia.Paideia
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.InputBox

/**
  * Treasury class represents the main contract for the Paideia Treasury which manages and holds assets and tokens
  * of the Paideia DAO treasury on Ergo Blockchain.
  *
  * @constructor creates a new instance of the Treasury class with given Paideia Contract Signature
  * @param contractSignature - the signature of the Paideia Contract entity
  */
class Treasury(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  /**
    * Creates an instance of the TreasuryBox object.
    *  @param ctx - The context of the blockchain
    *  @param daoConfig - The current configuration of DAO
    *  @param value - Long type value of nanoERG.
    *  @param tokens - Tokens that are used within the TreasuryBox.
    */
  def box(
    ctx: BlockchainContextImpl,
    daoConfig: DAOConfig,
    value: Long,
    tokens: List[ErgoToken]
  ): TreasuryBox = {
    val res = new TreasuryBox
    res.ctx      = ctx
    res.contract = contract
    res.value    = value
    res.tokens   = tokens
    res
  }

  /**
    * Constants for the Treasury contract are defined here. It can only contain objects that were there during the compilation time (e.g literals).
    *
    * Currently it contains `_IM_PAIDEIA_DAO_ACTION_TOKENID` constant.
    */
  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_DAO_ACTION_TOKENID",
      Paideia
        .getConfig(contractSignature.daoKey)
        .getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid)
    )
    cons
  }

  /**
    * It searches through all the boxes in the blockchain and matches the conditions to find required number of
    * boxes to fetch nanoERG and the provided array of ErgoTokens.
    *
    * @return Option[Array[InputBox]] if the required boxes found or else None.
    * @param nanoErgNeeded - Amount of nanoERG needed.
    * @param tokensNeeded - Array of ErgoTokens needed in the result.
    */
  def findBoxes(
    nanoErgNeeded: Long,
    tokensNeeded: Array[ErgoToken]
  ): Option[Array[InputBox]] = {
    var assetsFound  = false
    var nanoErgFound = 0L
    var tokensFound  = new HashMap[String, Long]()
    var result       = List[InputBox]()
    boxes.foreach((box: (String, InputBox)) => {
      if (!assetsFound || result.length < 20) {
        result = result.::(box._2)
        nanoErgFound += box._2.getValue()
        box._2
          .getTokens()
          .forEach((token: ErgoToken) =>
            tokensFound.put(
              token.getId().toString(),
              token.getValue() + tokensFound.getOrDefault(token.getId().toString(), 0L)
            )
          )
        assetsFound = nanoErgFound >= nanoErgNeeded && tokensNeeded.forall(
            (token: ErgoToken) =>
              token.getValue <= tokensFound.getOrDefault(token.getId().toString(), 0L)
          )
      } else Unit
    })
    if (result.length > 0) {
      Some(result.toArray)
    } else {
      None
    }
  }
}

/**
  * Companion Treasury object which extends Paideia Actor.
  */
object Treasury extends PaideiaActor {

  /**
    * The apply method creates and returns the Treasury contract's instance.
    * @param contractSignature - the signature of the Paideia Contract entity
    * @return Treasury class object
    */
  override def apply(contractSignature: PaideiaContractSignature): Treasury =
    getContractInstance[Treasury](contractSignature, new Treasury(contractSignature))
}
