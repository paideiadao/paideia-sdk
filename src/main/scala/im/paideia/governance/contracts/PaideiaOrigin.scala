package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import im.paideia.DAOConfig
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.boxes.PaideiaOriginBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import java.util.HashMap
import im.paideia.Paideia
import im.paideia.DAOConfigKey
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ErgoValue

class PaideiaOrigin(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig, daoTokensRemaining: Long): PaideiaOriginBox = {
        val res = new PaideiaOriginBox
        res.ctx = ctx
        res.value = 1000000L
        res.tokens = List(
            new ErgoToken(Env.paideiaOriginNFT,1L),
            new ErgoToken(Env.daoTokenId,daoTokensRemaining)
        )
        res.contract = contract
        res
    }

    override lazy val constants: HashMap[String,Object] = {
        val cons = new HashMap[String,Object]()
        val paideiaRef = Paideia._daoMap
        cons.put("_IM_PAIDEIA_FEES_CREATEDAO_ERG",ErgoValue.of(DAOConfigKey("im.paideia.fees.createdao.erg").hashedKey).getValue())
        cons.put("_IM_PAIDEIA_FEES_CREATEDAO_PAIDEIA",ErgoValue.of(DAOConfigKey("im.paideia.fees.createdao.paideia").hashedKey).getValue())
        cons.put("_IM_PAIDEIA_CONTRACTS_PROTODAO",ErgoValue.of(DAOConfigKey("im.paideia.contracts.protodao").hashedKey).getValue())
        cons.put("_IM_PAIDEIA_CONTRACTS_PROTODAOPROXY",ErgoValue.of(DAOConfigKey("im.paideia.contracts.protodaoproxy").hashedKey).getValue())
        cons.put("_IM_PAIDEIA_CONTRACTS_TREASURY",ErgoValue.of(DAOConfigKey("im.paideia.contracts.treasury").hashedKey).getValue())
        cons.put("_PAIDEIA_TOKEN_ID",ErgoId.create(Env.paideiaTokenId).getBytes())
        cons.put("_PAIDEIA_DAO_KEY",ErgoId.create(Env.paideiaDaoKey).getBytes())
        cons
    }
}

object PaideiaOrigin extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): PaideiaOrigin = 
        getContractInstance[PaideiaOrigin](contractSignature,new PaideiaOrigin(contractSignature))
}