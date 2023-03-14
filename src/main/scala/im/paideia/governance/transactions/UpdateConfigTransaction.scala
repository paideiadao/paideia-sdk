package im.paideia.governance.transactions

import im.paideia.governance.boxes.ActionUpdateConfigBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAO
import org.ergoplatform.appkit.InputBox
import im.paideia.common.transactions.PaideiaTransaction
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.common.filtering.CompareField
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import special.collection.Coll
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.ContextVar
import sigmastate.eval.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import im.paideia.common.contracts.Treasury

final case class UpdateConfigTransaction(
    _ctx: BlockchainContextImpl,
    dao: DAO,
    actionInput: InputBox
) extends PaideiaTransaction
{
    ctx = _ctx

    fee = 1000000L

    val actionInputBox = ActionUpdateConfigBox.fromInputBox(ctx,actionInput)

    val treasuryContract = Treasury(PaideiaContractSignature(daoKey=dao.key))

    val treasuryAddress = treasuryContract.contract.toAddress()

    val configContract = Config(PaideiaContractSignature(daoKey=dao.key))

    val configInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        dao.key,
        CompareField.ASSET,
        0
    ))(0)

    val proposalInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(dao.config.getArray(ConfKeys.im_paideia_dao_proposal_tokenid)).toString(),
        CompareField.ASSET,
        0
    )).filter(
        (box: InputBox) =>
            box.getRegisters().get(0).getValue().asInstanceOf[Coll[Int]](0) == actionInputBox.proposalId
    )(0)

    tokensToBurn = actionInput.getTokens().asScala.toList

    val actionContext = List(
        ContextVar.of(1.toByte, if (actionInputBox.remove.size > 0) dao.config.removeProof(actionInputBox.remove:_*) else ErgoValueBuilder.buildFor(Colls.fromArray(Array[Byte]()))),
        ContextVar.of(2.toByte, if (actionInputBox.update.size > 0) dao.config.updateProof(actionInputBox.update:_*) else ErgoValueBuilder.buildFor(Colls.fromArray(Array[Byte]()))),
        ContextVar.of(3.toByte, if (actionInputBox.insert.size > 0) dao.config.insertProof(actionInputBox.insert:_*) else ErgoValueBuilder.buildFor(Colls.fromArray(Array[Byte]())))
    )

    val context = List(
        ContextVar.of(0.toByte, dao.config.getProof(ConfKeys.im_paideia_contracts_config))
    )

    inputs = List(configInput,actionInput.withContextVars(context:_*))
    dataInputs = List(proposalInput)
    outputs = List(configContract.box(ctx,dao).outBox)

    changeAddress = treasuryAddress.getErgoAddress()
}
