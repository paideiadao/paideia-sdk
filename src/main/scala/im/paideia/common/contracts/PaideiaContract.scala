package im.paideia.common.contracts

import im.paideia.DAOConfig
import im.paideia.common.events.BlockEvent
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import im.paideia.common.boxes.PaideiaBox
import im.paideia.common.filtering.FilterNode
import org.ergoplatform.appkit.ErgoContract
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.sdk.JavaHelpers
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.restapi.client.ErgoTransaction
import org.ergoplatform.restapi.client.ErgoTransactionInput
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import scorex.crypto.hash.Blake2b256

import java.lang.{Byte => JByte}
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import scala.collection.mutable.Set
import scala.io.Source
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scorex.crypto.authds.ADDigest
import org.ergoplatform.appkit.AppkitHelpers
import scala.util.matching.Regex
import sigma.ast.ErgoTree
import sigma.Coll
import sigma.Colls
import org.ergoplatform.sdk.ContractTemplate
import sigmastate.lang.SigmaTemplateCompiler
import sigma.ast.Constant
import sigma.ast.SType
import java.net.URL
import java.io.File
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import io.circe.Json
import io.circe.parser._
import java.nio.file.attribute.FileTime
import java.nio.file.Path
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.transactions.UpdateOrRefreshTransaction
import org.ergoplatform.appkit.Address
import im.paideia.util.Env
import im.paideia.Paideia
import scala.collection.mutable.HashSet
import org.ergoplatform.sdk.ErgoId
import im.paideia.common.transactions.GarbageCollectTransaction

/** Represents a smart contract on the Paideia platform.
  *
  * @constructor
  *   creates a new [[PaideiaContract]] with the specified contract signature.
  * @param _contractSignature
  *   the digital signature of the contract
  */
class PaideiaContract(
  _contractSignature: PaideiaContractSignature,
  longLivingKey: Option[String]             = None,
  garbageCollectable: Option[Array[ErgoId]] = None
) {

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

  def sourcePath(extension: String): String = "/ergoscript/" + getClass
    .getSimpleName() + "/" + _contractSignature.version + "/" + getClass
    .getSimpleName() + extension

  lazy val ergoScriptURL: URL = getClass.getResource(
    sourcePath(".es")
  )

  /** The ErgoScript code of this contract.
    *
    * Reads from file "ergoscript/{Simple Class Name of this contract}/{The version
    * specified in the contract signature}.
    */
  lazy val ergoScript: (String, FileTime) = {
    val modified = Files.getLastModifiedTime(Paths.get(ergoScriptURL.getPath()))
    val baseScript = Source
      .fromFile(ergoScriptURL.getPath())
      .mkString
    val resolved       = resolveDependencies(baseScript, modified)
    var resolvedScript = resolved._1

    constants.toArray
      .sortBy(-1 * _._1.length())
      .foreach((kv: (String, Object)) => {
        resolvedScript = resolvedScript.replaceAll(kv._1, constantToString(kv._2))
      })

    (resolvedScript, resolved._2)
  }

  private def constantToString(c: Any): String = {
    val res = c match {
      case l: Long => l.toString().concat("L")
      case b: Byte => b.toString().concat(".toByte")
      case coll: Coll[_] =>
        "Coll(" ++ coll.toArray.map(constantToString(_)).mkString(",") ++ ")"
      case coll: Array[_] =>
        "Coll(" ++ coll.map(constantToString(_)).mkString(",") ++ ")"
      case o: Any => o.toString()
    }
    res
  }

  def resolveDependencies(
    sourceScript: String,
    sourceModified: FileTime,
    matches: Set[String] = new HashSet()
  ): (String, FileTime) = {
    val importPattern: Regex = "#import ([0-9a-zA-Z\\./]+);".r
    importPattern.findFirstMatchIn(sourceScript) match {
      case None => (sourceScript, sourceModified)
      case Some(importMatch) => {
        val matched = importMatch.subgroups(0)
        if (!matches.contains(matched)) {
          val importPath = Paths.get(
            getClass
              .getResource(
                "/ergoscript/" + matched
              )
              .getPath()
          )
          val importModified = Files.getLastModifiedTime(importPath)
          val importScript = Source
            .fromFile(
              importPath.toString()
            )
            .mkString
          matches.add(matched)
          val resolvedImport = resolveDependencies(importScript, importModified, matches)
          resolveDependencies(
            sourceScript.replaceFirst(
              importMatch.matched,
              resolvedImport._1
            ),
            if (resolvedImport._2.compareTo(sourceModified) < 0) sourceModified
            else resolvedImport._2,
            matches
          )
        } else {
          resolveDependencies(
            sourceScript.replaceFirst(
              importMatch.matched,
              ""
            ),
            sourceModified,
            matches
          )
        }
      }
    }
  }

  lazy val parameters: Map[String, Constant[SType]] =
    new HashMap[String, Constant[SType]]().toMap

  /** The Ergo tree for the ErgoScript code associated with this contract.
    */
  lazy val ergoTree: ErgoTree =
    contractTemplate.applyTemplate(Some(0), parameters)

  lazy val contractTemplate: ContractTemplate = {
    if (_contractSignature.version == "latest") {
      val templatePath = Paths.get(ergoScriptURL.getPath().replace(".es", ".json"))
      if (Files.exists(templatePath)) {
        val lastCompile = Files.getLastModifiedTime(templatePath)
        if (
          _contractSignature.version == "latest" && lastCompile.compareTo(
            ergoScript._2
          ) < 0
        ) {
          compileContract(templatePath)
        }
        val templateString = Files.readString(templatePath)
        val templateJson   = parse(templateString).right.get
        val res            = ContractTemplate.jsonEncoder.decoder(templateJson.hcursor)
        res.right.get
      } else {
        Files.createFile(templatePath)
        compileContract(templatePath)
      }
    } else {
      val templateString =
        Source.fromResource(sourcePath(".json")).mkString
      val templateJson = parse(templateString).right.get
      val res          = ContractTemplate.jsonEncoder.decoder(templateJson.hcursor)
      res.right.get
    }
  }

  def compileContract(templatePath: Path): ContractTemplate = {
    val template = SigmaTemplateCompiler(
      _contractSignature.networkType.networkPrefix
    ).compile(ergoScript._1)
    Files.writeString(
      templatePath,
      template.toJsonString,
      StandardOpenOption.TRUNCATE_EXISTING
    )
    template
  }

  /** The ErgoContract representing this contract.
    */
  lazy val contract: ErgoContract =
    new ErgoTreeContract(ergoTree, _contractSignature.networkType)

  /** Constants used in the contract code. It stores a collection of key-value pairs
    * representing the different constants.
    */
  lazy val constants: HashMap[String, Object] =
    new HashMap[String, Object]()

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
    if (boxes.contains(boxId)) {
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
    } else {
      false
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
            if (
              output.getErgoTree == ergoTree.bytesHex && validateBox(
                te.ctx,
                new InputBoxImpl(output)
              )
            )
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
      case cte: CreateTransactionsEvent => {
        if (longLivingKey.isDefined && getUtxoSet.size > 0) {
          val correctContract = Paideia.instantiateContractInstance(
            Paideia
              .getConfig(contractSignature.daoKey)(longLivingKey.get)
              .asInstanceOf[PaideiaContractSignature]
              .withDaoKey(contractSignature.daoKey)
          )
          val outdatedBoxes = getUtxoSet.toList
            .flatMap(boxId => {
              if (boxes(boxId).getCreationHeight() < cte.height - 504000) {
                Some(
                  boxes(boxId)
                )
              } else {
                None
              }
            })
          if (outdatedBoxes.length > 0) {
            PaideiaEventResponse(
              1,
              List(
                UpdateOrRefreshTransaction(
                  cte.ctx,
                  outdatedBoxes,
                  longLivingKey.get,
                  Paideia.getDAO(contractSignature.daoKey),
                  Address.fromErgoTree(
                    correctContract.ergoTree,
                    cte.ctx.getNetworkType()
                  ),
                  Address.create(Env.operatorAddress)
                )
              )
            )
          } else {
            PaideiaEventResponse(0)
          }
        } else if (garbageCollectable.isDefined) {
          val garbage =
            getUtxoSet.filter(boxes(_).getCreationHeight() < cte.height - 788400)
          if (garbage.size > 0) {
            PaideiaEventResponse(
              1,
              garbage.toList.map(g =>
                GarbageCollectTransaction(
                  cte.ctx,
                  boxes(g),
                  garbageCollectable.get,
                  Address.create(Env.operatorAddress)
                )
              )
            )
          } else {
            PaideiaEventResponse(0)
          }
        } else {
          PaideiaEventResponse(0)
        }
      }
      case _ => PaideiaEventResponse(0)
    }
  }

  // This should be overridden in sub classes
  def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = ???

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
