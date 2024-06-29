package im.paideia.governance.transactions

import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Treasury
import im.paideia.common.filtering.CompareField
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.common.transactions.PaideiaTransaction
import im.paideia.governance.boxes.ActionUpdateConfigBox
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigma.Colls
import sigma.Coll

import scala.collection.JavaConverters._
import scorex.crypto.authds.ADDigest
import sigma.AvlTree
import im.paideia.util.TxTypes

/** A transaction that updates configuration of the Paideia DAO and creates a new Config
  * box while consuming an existing `configInput` box and an `actionInput` box which
  * contains proposals to update/insert/remove state.
  *
  * @param _ctx
  *   The context of the blockchain in which this transaction is made
  * @param dao
  *   The instance of [[DAO]] used in this transaction
  * @param actionInput
  *   The action box which contains proposals to update/insert/remove
  * @return
  *   A new instance of UpdateConfigTransaction with configured properties
  */
final case class UpdateConfigTransaction(
  _ctx: BlockchainContextImpl,
  dao: DAO,
  actionInput: InputBox
) extends PaideiaTransaction {

  ctx = _ctx

  fee = 1000000L

  // Create ActionUpdateConfigBox from the input
  val actionInputBox = ActionUpdateConfigBox.fromInputBox(ctx, actionInput)

  // Instantiate a treasury contract and get its address
  val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
  val treasuryAddress  = treasuryContract.contract.toAddress()

  // Create a config contract instance and retrieve Config box using indexed tokens from DAO
  val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

  val configInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      dao.key,
      CompareField.ASSET,
      0
    )
  )(0)

  // Filter proposalInput with value matching proposalId
  val proposalInput = Paideia
    .getBox(
      new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(dao.config.getArray(ConfKeys.im_paideia_dao_proposal_tokenid))
          .toString(),
        CompareField.ASSET,
        0
      )
    )
    .filter((box: InputBox) =>
      box
        .getRegisters()
        .get(0)
        .getValue()
        .asInstanceOf[Coll[Int]](0) == actionInputBox.proposalId
    )(0)

  // Get tokens to be burned from actionInput
  tokensToBurn = actionInput.getTokens().asScala.toList

  val configDigest =
    ADDigest @@ configInput
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

  var resultingDigest: Option[ADDigest] = Some(configDigest)

  // Create action context variables
  val actionContext = List(
    ContextVar.of(
      1.toByte,
      if (actionInputBox.remove.size > 0) {
        val result =
          dao.config.removeProof(actionInputBox.remove: _*)(Left(resultingDigest.get))
        resultingDigest = Some(result._2)
        result._1
      } else ErgoValueBuilder.buildFor(Colls.fromArray(Array[Byte]()))
    ),
    ContextVar.of(
      2.toByte,
      if (actionInputBox.update.size > 0) {
        val result =
          dao.config.updateProof(actionInputBox.update: _*)(Left(resultingDigest.get))
        resultingDigest = Some(result._2)
        result._1
      } else ErgoValueBuilder.buildFor(Colls.fromArray(Array[Byte]()))
    ),
    ContextVar.of(
      3.toByte,
      if (actionInputBox.insert.size > 0) {
        val result =
          dao.config.insertProof(actionInputBox.insert: _*)(Left(resultingDigest.get))
        resultingDigest = Some(result._2)
        result._1
      } else ErgoValueBuilder.buildFor(Colls.fromArray(Array[Byte]()))
    )
  )

  val configProof =
    dao.config.getProof(ConfKeys.im_paideia_contracts_config)(resultingDigest)

  // Create context variables
  val context = List(
    ContextVar.of(
      0.toByte,
      configProof
    )
  )

  // Set inputs, dataInputs, outputs and changeAddress
  inputs = List(
    configInput
      .withContextVars(
        ContextVar.of(1.toByte, configProof),
        ContextVar.of(0.toByte, TxTypes.CHANGE_CONFIG)
      ),
    actionInput.withContextVars((context ++ actionContext): _*)
  )
  dataInputs = List(proposalInput)
  outputs    = List(configContract.box(ctx, dao, resultingDigest).outBox)

  changeAddress = treasuryAddress
}
