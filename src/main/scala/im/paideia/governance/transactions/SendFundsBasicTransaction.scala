package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAO
import org.ergoplatform.appkit.InputBox
import im.paideia.governance.boxes.ActionSendFundsBasicBox
import im.paideia.governance.contracts.ActionSendFundsBasic
import im.paideia.common.contracts.Treasury
import im.paideia.util.ConfKeys
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.OutBoxImpl
import org.ergoplatform.ErgoBoxCandidate
import special.sigma.Box
import sigmastate.eval.CostingSigmaDslBuilder
import org.ergoplatform.appkit.ErgoToken
import scala.collection.JavaConverters._
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.appkit.ContextVar
import im.paideia.Paideia
import im.paideia.common.filtering._
import org.ergoplatform.appkit.ErgoId
import special.collection.Coll

final case class SendFundsBasicTransaction(
    _ctx: BlockchainContextImpl,
    dao: DAO,
    actionInput: InputBox
) extends PaideiaTransaction
{
    ctx = _ctx

    fee = 1000000L

    val actionInputBox = ActionSendFundsBasicBox.fromInputBox(ctx,actionInput)

    val treasuryContract = Treasury(PaideiaContractSignature(daoKey=dao.key))

    val treasuryAddress = treasuryContract.contract.toAddress()

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

    
    val fundsNeeded = actionInputBox.fundsNeeded
    val coveringTreasuryBoxes = treasuryContract.findBoxes(fundsNeeded._1,fundsNeeded._2).get

    val selfOutput = if (actionInputBox.repeats>0) {
        List(
            ActionSendFundsBasicBox(
                ctx,
                dao,
                actionInputBox.proposalId,
                actionInputBox.optionId,
                actionInputBox.repeats-1,
                actionInputBox.activationTime+actionInputBox.repeatDelay,
                actionInputBox.repeatDelay,
                actionInputBox.outputs,
                ActionSendFundsBasic(PaideiaContractSignature(daoKey=dao.key))).outBox
        )
    } else { List[OutBox]() }

    tokensToBurn = if (actionInputBox.repeats>0) {
        List[ErgoToken]()
    } else { actionInput.getTokens().asScala.toList }

    val context = List(
        ContextVar.of(0.toByte,dao.config.getProof(ConfKeys.im_paideia_contracts_treasury))
    )

    inputs = List(actionInput.withContextVars(context:_*))++coveringTreasuryBoxes
    dataInputs = List(configInput,proposalInput)
    outputs = actionInputBox.outputs.map{(b: Box) =>
        new OutBoxImpl((new CostingSigmaDslBuilder()).toErgoBox(b).toCandidate)
    }.toList++selfOutput

    changeAddress = treasuryAddress.getErgoAddress()
}
