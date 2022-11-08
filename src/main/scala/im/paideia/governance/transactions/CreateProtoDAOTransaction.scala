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
import im.paideia.governance.contracts.Mint
import im.paideia.DAOConfigValueDeserializer
import im.paideia.governance.boxes.ProtoDAOProxyBox
import org.ergoplatform.appkit.ErgoId

case class CreateProtoDAOTransaction(
    _ctx: BlockchainContextImpl,
    protoDAOProxyInput: InputBox,
    _changeAddress: ErgoAddress
) extends PaideiaTransaction {
    val protoDAOProxyInputBox = ProtoDAOProxyBox.fromInputBox(_ctx,protoDAOProxyInput)
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
    val newDAO = new DAO(protoDAOProxyInput.getId().toString(),newDAOConfig)
    Paideia.addDAO(newDAO)

    val paideiaOriginInput = Paideia.getBox(new FilterLeaf[String](FilterType.FTEQ,Env.paideiaOriginNFT,CompareField.ASSET,0))(0)
    val paideiaOriginOutput = PaideiaOrigin(PaideiaContractSignature(networkType = _ctx.getNetworkType(),daoKey=Env.paideiaDaoKey)).box(_ctx,paideiaConfig,paideiaOriginInput.getTokens().get(1).getValue()-1L)
    val paideiaTreasuryOutput = Treasury(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=Env.paideiaDaoKey)).box(_ctx,paideiaConfig,ergFee+1000000L,List(new ErgoToken(Env.paideiaTokenId,paideiaFee)))
    val mintOutput = Mint(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=Env.paideiaDaoKey)).box(
        _ctx,
        protoDAOProxyInput.getId().toString(),
        1L,
        protoDAOProxyInputBox.daoName++" DAO Key",
        protoDAOProxyInputBox.daoName++" DAO Key",
        0
    )
    val checkDigest = paideiaConfigBox.getRegisters().get(0).getValue()
    val checkDigest2 = paideiaConfig._config.ergoValue.getValue()
    val contextVarPaideiaOrigin = ContextVar.of(0.toByte,paideiaConfig.getProof(
        "im.paideia.fees.createdao.erg",
        "im.paideia.fees.createdao.paideia",
        "im.paideia.contracts.protodao",
        "im.paideia.contracts.protodaoproxy",
        "im.paideia.contracts.treasury"
    ))
    val contextVarsProtoDAOProxy = List(
        ContextVar.of(0.toByte,paideiaConfig.getProof(
            "im.paideia.contracts.protodao",
            "im.paideia.contracts.mint"
            )),
        ContextVar.of(1.toByte,newDAOConfig._config.ergoValue),
        ContextVar.of(2.toByte,newDAOConfig.insertProof(
            ("im.paideia.dao.name",DAOConfigValueSerializer(protoDAOProxyInputBox.daoName)),
            ("im.paideia.dao.tokenid",DAOConfigValueSerializer(ErgoId.create(protoDAOProxyInputBox.daoGovernanceTokenId).getBytes())),
            ("im.paideia.dao.key",DAOConfigValueSerializer[Array[Byte]](protoDAOProxyInput.getId().getBytes()))
        ))
    )
    val protoDAOOutput = ProtoDAO(PaideiaContractSignature(daoKey=Env.paideiaDaoKey)).box(_ctx,newDAO,protoDAOProxyInputBox.stakePoolSize)
    ctx = _ctx
    fee = 1000000
    changeAddress = _changeAddress
    inputs = List[InputBox](protoDAOProxyInput.withContextVars(contextVarsProtoDAOProxy:_*),paideiaOriginInput.withContextVars(contextVarPaideiaOrigin))
    dataInputs = List[InputBox](paideiaConfigBox)
    outputs = List[OutBox](protoDAOOutput.outBox,paideiaOriginOutput.outBox,paideiaTreasuryOutput.outBox,mintOutput.outBox)
}
