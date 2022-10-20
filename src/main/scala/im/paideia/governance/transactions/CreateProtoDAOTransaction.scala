package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.CompareField
import im.paideia.common.filtering.FilterType
import im.paideia.Paideia
import im.paideia.util.Env
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.contracts.Treasury
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.ErgoAddress
import im.paideia.governance.contracts.PaideiaOrigin
import org.ergoplatform.appkit.ContextVar
import im.paideia.DAOConfig
import im.paideia.DAO
import special.collection.Coll
import im.paideia.DAOConfigValueSerializer

case class CreateProtoDAOTransaction(
    _ctx: BlockchainContextImpl,
    protoDAOProxyInput: InputBox,
    _changeAddress: ErgoAddress
) extends PaideiaTransaction {
    val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)
    val ergFee = paideiaConfig[Long]("im.paideia.fees.createdao.erg")
    val paideiaFee = paideiaConfig[Long]("im.paideia.fees.createdao.paideia")
    val paideiaConfigBox = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        Env.paideiaDaoKey,
        CompareField.ASSET,
        0
    ))(0)
    val newDAOConfig = DAOConfig()
    Paideia.addDAO(new DAO(protoDAOProxyInput.getId().toString(),newDAOConfig))
    val daoParams : Coll[Coll[Byte]] = protoDAOProxyInput.getRegisters().get(0).getValue().asInstanceOf[Coll[Coll[Byte]]]
    val paideiaOriginInput = Paideia.getBox(new FilterLeaf[String](FilterType.FTEQ,Env.paideiaOriginNFT,CompareField.ASSET,0))(0)
    val paideiaOriginOutput = PaideiaOrigin(PaideiaContractSignature(networkType = _ctx.getNetworkType(),daoKey=Env.paideiaDaoKey)).box(_ctx,paideiaConfig,paideiaOriginInput.getTokens().get(1).getValue()-1L)
    val paideiaTreasuryOutput = Treasury(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=Env.paideiaDaoKey)).box(_ctx,paideiaConfig,ergFee+1000000L,List(new ErgoToken(Env.paideiaTokenId,paideiaFee)))
    val checkDigest = paideiaConfigBox.getRegisters().get(0).getValue()
    val checkDigest2 = paideiaConfig._config.ergoValue.getValue()
    val contextVarPaideiaOrigin = ContextVar.of(0.toByte,paideiaConfig.getProof(List(
        "im.paideia.fees.createdao.erg",
        "im.paideia.fees.createdao.paideia",
        "im.paideia.contracts.protodao",
        "im.paideia.contracts.protodaoproxy",
        "im.paideia.contracts.treasury"
    )))
    val test = DAOConfigValueSerializer[Array[Byte]](protoDAOProxyInput.getId().getBytes())
    val contextVarsProtoDAOProxy = List(
        ContextVar.of(0.toByte,paideiaConfig.getProof(List("im.paideia.contracts.protodao"))),
        ContextVar.of(1.toByte,newDAOConfig._config.ergoValue),
        ContextVar.of(2.toByte,newDAOConfig.insertProof(List(
            ("im.paideia.dao.name",daoParams(0).toArray),
            ("im.paideia.dao.tokenid",daoParams(1).toArray),
            ("im.paideia.dao.key",DAOConfigValueSerializer[Array[Byte]](protoDAOProxyInput.getId().getBytes()))
        )))
    )
    val protoDAOOutput = ProtoDAO(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=Env.paideiaDaoKey)).box(_ctx,newDAOConfig)
    ctx = _ctx
    fee = 1000000
    changeAddress = _changeAddress
    inputs = List[InputBox](protoDAOProxyInput.withContextVars(contextVarsProtoDAOProxy:_*),paideiaOriginInput.withContextVars(contextVarPaideiaOrigin))
    dataInputs = List[InputBox](paideiaConfigBox)
    outputs = List[OutBox](protoDAOOutput.outBox,paideiaOriginOutput.outBox,paideiaTreasuryOutput.outBox)
}
