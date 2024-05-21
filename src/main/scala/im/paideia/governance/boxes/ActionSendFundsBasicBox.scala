package im.paideia.governance.boxes

import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.boxes.PaideiaBox
import im.paideia.governance.contracts.ActionSendFundsBasic
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoToken
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scorex.crypto.hash.Blake2b256
import sigma.Colls
import sigma.Coll
import sigma.Box

import scala.collection.mutable.HashMap

/** A box that represents an action of sending funds to another address.
  * @param _ctx
  *   The context of blockchain related values.
  * @param dao
  *   the DAO information for this box.
  * @param proposalId
  *   Unique identifier for the proposal.
  * @param optionId
  *   The option ID linked with the specified proposal.
  * @param repeats
  *   Number of times the funds will be transferred.
  * @param activationTime
  *   Unix timestamp when funds transfer gets activated.
  * @param repeatDelay
  *   Time in milliseconds between each consecutive pair of transactions.
  * @param outputs
  *   Array of destination boxes where funds will be transferred.
  * @param useContract
  *   It is used in constructing the contract for the box.
  */
final case class ActionSendFundsBasicBox(
  _ctx: BlockchainContextImpl,
  dao: DAO,
  proposalId: Int,
  optionId: Int,
  repeats: Int,
  activationTime: Long,
  repeatDelay: Long,
  outputs: Array[Box],
  useContract: ActionSendFundsBasic
) extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract

  /** A list of Ergo tokens within the transaction.
    * @return
    *   a list of ErgoTokens
    */
  override def tokens: List[ErgoToken] = List(
    new ErgoToken(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid), 1L)
  )

  /** List of contents of the register of FullEncoder type. Here, This method defines
    * content of first register by concatenating four registers - proposalId, optionId,
    * repeats, activationTime, repeatDelay. Second register returns array of output boxes
    * @return
    *   list of registers containing all values
    */
  override def registers: List[ErgoValue[_]] = List(
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        Array(proposalId.toLong, optionId.toLong, repeats, activationTime, repeatDelay)
      )
    ),
    ErgoValueBuilder.buildFor(Colls.fromArray(outputs))
  )

  /** Determines funds required for making the necessary transactions.
    * @return
    *   Tuple consisting of nano-ergs and non-ergo tokens required.
    */
  def fundsNeeded: (Long, Array[ErgoToken]) = {
    val nanoErgsNeeded = outputs.foldLeft(0L) { (z: Long, b: Box) =>
      z + b.value
    }
    val tokensNeeded = HashMap[Coll[Byte], Long]()
    outputs.foreach(
      _.tokens.toArray.foreach((token: (Coll[Byte], Long)) =>
        if (tokensNeeded.contains(token._1))
          tokensNeeded(token._1) += token._2
        else
          tokensNeeded(token._1) = token._2
      )
    )
    (
      nanoErgsNeeded,
      tokensNeeded
        .map((kv: (Coll[Byte], Long)) => new ErgoToken(kv._1.toArray, kv._2))
        .toArray
    )
  }
}

object ActionSendFundsBasicBox {

  /** Parse from existing input box to ActionSendFundsBasicBox instance
    * @param ctx
    *   The context of blockchain related values.
    * @param inp
    *   An existing box
    * @return
    *   ActionSendFundsBasicBox instance
    */
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ActionSendFundsBasicBox = {
    val contract = ActionSendFundsBasic
      .contractInstances(Blake2b256(inp.getErgoTree().bytes).array.toList)
      .asInstanceOf[ActionSendFundsBasic]
    val params = inp.getRegisters().get(0).getValue.asInstanceOf[Coll[Long]]
    ActionSendFundsBasicBox(
      ctx,
      Paideia.getDAO(contract.contractSignature.daoKey),
      params(0).toInt,
      params(1).toInt,
      params(2).toInt,
      params(3),
      params(4),
      inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Box]].toArray,
      contract
    )
  }
}
