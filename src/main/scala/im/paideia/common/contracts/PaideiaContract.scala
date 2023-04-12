package im.paideia.common.contracts

import im.paideia.DAOConfig
import im.paideia.DAOConfigValue
import im.paideia.common.events.BlockEvent
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import im.paideia.common.boxes.PaideiaBox
import im.paideia.common.filtering.FilterNode
import org.ergoplatform.appkit.ErgoContract
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.JavaHelpers
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.restapi.client.ErgoTransaction
import org.ergoplatform.restapi.client.ErgoTransactionInput
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import scorex.crypto.hash.Blake2b256
import sigmastate.Values
import special.collection.Coll
import sigmastate.eval.Colls

import java.lang.{Byte => JByte}
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import scala.collection.mutable.Set
import scala.io.Source
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scorex.crypto.authds.ADDigest

/** Represents a smart contract on the Paideia platform.
  *
  * @constructor
  *   creates a new [[PaideiaContract]] with the specified contract signature.
  * @param _contractSignature
  *   the digital signature of the contract
  */
class PaideiaContract(_contractSignature: PaideiaContractSignature) {

  /** The unspent transaction output set for this contract.
    *
    * It is an immutable set containing unique identifiers of all boxes that are neither
    * spent nor in mempool.
    */
  val utxos: Set[String] = Set[String]()

  /** The mutable transaction output set for this contract.
    *
    * It is an immutable set containing unique identifiers of boxes currently in mempool.
    */
  val mutxos: Set[String] = Set[String]()

  /** The mutable spent transaction output set for this contract.
    *
    * It is an immutable set containing unique identifiers of boxes that have been spent
    * and are now in mempool.
    */
  val mspent: Set[String] = Set[String]()

  /** A set of key-value pairs representing all the input boxes.
    *
    * Each input box is identified by a unique identifier which maps to the input box
    * object itself.
    */
  val boxes: HashMap[String, InputBox] = HashMap[String, InputBox]()

  /** The ErgoScript code of this contract.
    *
    * Reads from file "ergoscript/{Simple Class Name of this contract}/{The version
    * specified in the contract signature}.
    */
  lazy val ergoScript: String = Source
    .fromResource(
      "ergoscript/" + getClass
        .getSimpleName() + "/" + _contractSignature.version + "/" + getClass
        .getSimpleName() + ".es"
    )
    .mkString

  /** The Ergo tree root hash for the ErgoScript code associated with this contract.
    */
  lazy val ergoTree: Values.ErgoTree = {
    JavaHelpers.compile(
      constants,
      ergoScript,
      _contractSignature.networkType.networkPrefix
    )
  }

  /** The ErgoContract representing this contract.
    */
  lazy val contract: ErgoContract =
    new ErgoTreeContract(ergoTree, _contractSignature.networkType)

  /** Constants used in the contract code. It stores a collection of key-value pairs
    * representing the different constants.
    */
  lazy val constants: java.util.HashMap[String, Object] =
    new java.util.HashMap[String, Object]()

  /** The digital signature of this contract.
    */
  lazy val contractSignature: PaideiaContractSignature = {
    PaideiaContractSignature(
      getClass().getCanonicalName(),
      _contractSignature.version,
      _contractSignature.networkType,
      Blake2b256(ergoTree.bytes).array.toList,
      _contractSignature.daoKey
    )
  }

  /** Clear the boxes data.
    */
  def clearBoxes(): Unit = {
    utxos.clear()
    mutxos.clear()
    mspent.clear()
    boxes.clear()
  }

  /** Marks a specified box ID as spent and removes its entry from boxes map.
    *
    * @param boxId
    *   the string representation of a box ID.
    * @param mempool
    *   a boolean flag indicating if the box is also removed from mempool or not.
    * @return
    *   true if the box was successfully removed otherwise false.
    */
  def spendBox(boxId: String, mempool: Boolean, rollback: Boolean = false): Boolean = {
    if (rollback) {
      if (mempool) {
        mspent.remove(boxId)
      } else {
        utxos.add(boxId)
      }
    } else {
      if (mempool) {
        mspent.add(boxId)
      } else {
        utxos.remove(boxId) || mspent.remove(boxId)
      }
    }
  }

  def clearSpentBoxes {
    boxes.foreach(f =>
      if (!(utxos.contains(f._1) || mutxos.contains(f._1))) boxes -= f._1
    )
  }

  /** Add a new InputBox object to the boxes and utxos or mutxos set.
    *
    * @param box
    *   the InputBox instance to be added.
    * @param mempool
    *   a boolean flag indicating if the box should be added to mempool or not.
    * @return
    *   true if the box is added to either utxos or mutxos set. Otherwise false.
    */
  def newBox(box: InputBox, mempool: Boolean, rollback: Boolean = false): Boolean = {
    if (rollback) {
      if (mempool) {
        mutxos.remove(box.getId().toString())
      } else {
        utxos.remove(box.getId().toString()) || mutxos.remove(box.getId().toString())
      }
    } else {
      if (mempool) {
        boxes.put(box.getId().toString(), box)
        mutxos.add(box.getId().toString())
      } else {
        boxes.put(box.getId().toString(), box)
        utxos.add(box.getId().toString()) || mutxos.remove(box.getId().toString())
      }
    }
  }

  /** Returns the set of unspent transaction outputs (utxo).
    *
    * @return
    *   Set of unspent transaction outputs.
    */
  def getUtxoSet: Set[String] = utxos ++ mutxos -- mspent

  /** Handle Paideia events, such as `TransactionEvent` and `BlockEvent`.
    *
    * @param event
    *   Paideia event to handle.
    * @return
    *   Paideia event response.
    */
  def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    event match {
      case te: TransactionEvent => {
        val handledInputs =
          te.tx.getInputs().asScala.map { (input: ErgoTransactionInput) =>
            spendBox(input.getBoxId(), te.mempool, te.rollback)
          }
        val handledOutputs =
          te.tx.getOutputs().asScala.map { (output: ErgoTransactionOutput) =>
            if (output.getErgoTree == ergoTree.bytesHex)
              newBox(new InputBoxImpl(output), te.mempool, te.rollback)
            else false
          }
        if (handledInputs.exists(identity) || handledOutputs.exists(identity))
          PaideiaEventResponse(1)
        else PaideiaEventResponse(0)
      }
      case be: BlockEvent => {
        PaideiaEventResponse.merge(
          be.block
            .getBlockTransactions()
            .getTransactions()
            .asScala
            .map { (tx: ErgoTransaction) =>
              handleEvent(
                TransactionEvent(be.ctx, false, tx, be.block.getHeader().getHeight())
              )
            }
            .toList
        )
      }
      case _ => PaideiaEventResponse(0)
    }
  }

  def getConfigContext(configDigest: Option[ADDigest]): ErgoValue[Coll[java.lang.Byte]] =
    ErgoValueBuilder.buildFor(Colls.fromArray(Array[Byte]()))

  /** Returns a list of input boxes filtered by the given filter node.
    *
    * @param boxFilter
    *   The filter used to select input boxes.
    * @return
    *   List of input boxes that meet the filter criteria.
    */
  def getBox(boxFilter: FilterNode): List[InputBox] = {
    getUtxoSet
      .map(boxes(_))
      .filter(boxFilter.matchBox(_))
      .toList
      .sortBy(-1 * _.getCreationHeight())
  }

}
