package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import im.paideia.governance.boxes.ProposalBasicBox
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.filtering._
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import special.collection.Coll
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.common.contracts.OperatorIncentive
import im.paideia.util.Env
import org.ergoplatform.ErgoAddress
import im.paideia.common.contracts.Treasury
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.ContextVar

final case class EvaluateProposalBasicTransaction(
    _ctx: BlockchainContextImpl,
    dao: DAO,
    proposalInput: InputBox,
    _changeAddress: ErgoAddress
    ) extends PaideiaTransaction
{
    ctx = _ctx
    changeAddress = _changeAddress

    val proposalInputBox = ProposalBasicBox.fromInputBox(_ctx,proposalInput)

    val paideiaConfigInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        Env.paideiaDaoKey,
        CompareField.ASSET,
        0
    ))(0)

    val configInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        dao.key,
        CompareField.ASSET,
        0
    ))(0)

    val stakeStateInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid)).toString(),
        CompareField.ASSET,
        0
    ))(0)

    val totalStaked = stakeStateInput.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]](2)
    val quorumNeeded = dao.config[Long](ConfKeys.im_paideia_dao_quorum)

    val quorumMet = proposalInputBox.totalVotes > (totalStaked*quorumNeeded/1000)

    val proposalBasicOut = ProposalBasic(PaideiaContractSignature(daoKey=dao.key)).box(
        _ctx,
        proposalInputBox.proposalIndex,
        proposalInputBox.voteCount,
        proposalInputBox.totalVotes,
        proposalInputBox.endTime,
        if (quorumMet) 1 else -1
        )

    val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)
    val treasuryOut = Treasury(PaideiaContractSignature(Env.paideiaDaoKey)).box(
        ctx,
        paideiaConfig,
        1000000L,
        List(new ErgoToken(Env.paideiaTokenId,paideiaConfig(ConfKeys.im_paideia_fees_createproposal_paideia))))

    val context = List(
        ContextVar.of(0.toByte,paideiaConfig.getProof(
            ConfKeys.im_paideia_fees_createproposal_paideia,
            ConfKeys.im_paideia_contracts_treasury
        )),
        ContextVar.of(1.toByte,dao.config.getProof(
            ConfKeys.im_paideia_dao_quorum,
            ConfKeys.im_paideia_staking_state_tokenid
        ))
    )

    inputs = List(proposalInput.withContextVars(context:_*))
    dataInputs = List(paideiaConfigInput,configInput,stakeStateInput)
    outputs = List(proposalBasicOut.outBox,treasuryOut.outBox)

    fee = 1000000L
}   
