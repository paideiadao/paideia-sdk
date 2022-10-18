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

case class CreateProtoDAOTransaction(
    _ctx: BlockchainContextImpl,
    protoDAOProxyInput: InputBox,
    _changeAddress: ErgoAddress
) extends PaideiaTransaction {
    val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)
    val ergFee = paideiaConfig[Long]("im.paideia.fees.createDAO.erg")
    val paideiaFee = paideiaConfig[Long]("im.paideia.fees.createDAO.paideia")
    val paideiaConfigBox = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        Env.paideiaDaoKey,
        CompareField.ASSET,
        0
    ))(0)
    val paideiaOriginInput = Paideia.getBox(new FilterLeaf[String](FilterType.FTEQ,Env.paideiaOriginNFT,CompareField.ASSET,0))(0)
    val protoDAOOutput = ProtoDAO(PaideiaContractSignature(networkType=_ctx.getNetworkType())).box(_ctx,paideiaConfig)
    val paideiaOriginOutput = PaideiaOrigin(PaideiaContractSignature(networkType = _ctx.getNetworkType())).box(_ctx,paideiaConfig,paideiaOriginInput.getTokens().get(1).getValue()-1L)
    val paideiaTreasuryOutput = Treasury(PaideiaContractSignature(networkType=_ctx.getNetworkType())).box(_ctx,paideiaConfig,ergFee,List(new ErgoToken(Env.paideiaTokenId,paideiaFee)))

    ctx = _ctx
    fee = 1000000
    changeAddress = _changeAddress
    inputs = List[InputBox](protoDAOProxyInput,paideiaOriginInput)
    dataInputs = List[InputBox](paideiaConfigBox)
    outputs = List[OutBox](protoDAOOutput.outBox,paideiaOriginOutput.outBox,paideiaTreasuryOutput.outBox)
}
