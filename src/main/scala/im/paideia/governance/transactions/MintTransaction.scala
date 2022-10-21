package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.ErgoAddress
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.util.Env
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.contracts.Mint
import org.ergoplatform.appkit.ContextVar
import im.paideia.DAOConfigKey
import im.paideia.DAOConfigValueSerializer
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.filtering._
import org.ergoplatform.appkit.OutBox

final case class MintTransaction(_ctx: BlockchainContextImpl,
    protoDAOInput: InputBox,
    daoConfig: DAOConfig,
    tokenToMint: String,
    _changeAddress: ErgoAddress) extends PaideiaTransaction 
{
    val paideiaConfigBox = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        Env.paideiaDaoKey,
        CompareField.ASSET,
        0
    ))(0)
    val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)
    val daoName = daoConfig[String]("im.paideia.dao.name")
    val tokenInfo = tokenToMint match {
        case "im.paideia.dao.proposal.tokenid" => (daoName++" Proposal", Long.MaxValue)
        case "im.paideia.dao.vote.tokenid" => (daoName++" Vote", Long.MaxValue)
        case "im.paideia.dao.action.tokenid" => (daoName++" Action", Long.MaxValue)
    }
    val mintOutput = Mint(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=Env.paideiaDaoKey)).box(
        _ctx,
        protoDAOInput.getId().toString(),
        tokenInfo._2,
        tokenInfo._1,
        tokenInfo._1,
        0
    )
    val contextVarsProtoDAO = List(
        ContextVar.of(0.toByte,paideiaConfig.getProof(List(
            "im.paideia.contracts.protodao",
            "im.paideia.contracts.mint"
            ))),
        ContextVar.of(1.toByte,DAOConfigKey(tokenToMint).ergoValue),
        ContextVar.of(2.toByte,daoConfig.getProof(List("im.paideia.dao.name"))),
        ContextVar.of(3.toByte,daoConfig.insertProof(List(
            (tokenToMint,DAOConfigValueSerializer[Array[Byte]](protoDAOInput.getId().getBytes()))
        )))
    )
    val protoDAOOutput = ProtoDAO(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=Env.paideiaDaoKey)).box(_ctx,daoConfig)
    ctx = _ctx
    fee = 1000000
    changeAddress = _changeAddress
    inputs = List[InputBox](protoDAOInput.withContextVars(contextVarsProtoDAO:_*))
    dataInputs = List[InputBox](paideiaConfigBox)
    outputs = List[OutBox](protoDAOOutput.outBox,mintOutput.outBox)
}
