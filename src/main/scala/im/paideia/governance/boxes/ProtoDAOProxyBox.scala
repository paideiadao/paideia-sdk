package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.boxes.ConfigBox
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import im.paideia.DAOConfigValue
import im.paideia.DAOConfigValueSerializer
import org.ergoplatform.appkit.ErgoId
import special.collection.Coll
import org.ergoplatform.appkit.ErgoType
import special.collection.CollOverArray
import im.paideia.governance.contracts.ProtoDAOProxy
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfigValueDeserializer
import im.paideia.Paideia
import scorex.crypto.hash.Blake2b256
import im.paideia.util.ConfKeys
import sigmastate.eval.Colls

case class ProtoDAOProxyBox(_ctx: BlockchainContextImpl, paideiaDaoConfig: DAOConfig, useContract: ProtoDAOProxy, daoName: String, daoGovernanceTokenId: String, stakePoolSize: Long) extends PaideiaBox {
    ctx = _ctx
    value = ProtoDAO.tokensToMint.size*2000000L+paideiaDaoConfig[Long](ConfKeys.im_paideia_fees_createdao_erg)+3000000L
    contract = useContract.contract

    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValueBuilder.buildFor(Colls.fromArray(Array(
                Colls.fromArray(DAOConfigValueSerializer(daoName)),
                Colls.fromArray(DAOConfigValueSerializer(ErgoId.create(daoGovernanceTokenId).getBytes().asInstanceOf[Array[Byte]]))
            ))),
            ErgoValueBuilder.buildFor(Colls.fromArray(Array(stakePoolSize)))
        )
    }

    override def tokens: List[ErgoToken] = {
        {if (paideiaDaoConfig[Long](ConfKeys.im_paideia_fees_createdao_paideia) > 0L) 
            List(
                new ErgoToken(Env.paideiaTokenId,paideiaDaoConfig(ConfKeys.im_paideia_fees_createdao_paideia))
            ) 
        else 
            List[ErgoToken]()}++
        {if (stakePoolSize > 0L) 
            List(
                new ErgoToken(daoGovernanceTokenId,stakePoolSize)
            ) 
        else 
            List[ErgoToken]()}
    }
}

object ProtoDAOProxyBox {
    def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ProtoDAOProxyBox = {
        val daoName = DAOConfigValueDeserializer.deserialize(inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Coll[Byte]]](0).toArray).asInstanceOf[String]
        val daoTokenId = new ErgoId(DAOConfigValueDeserializer.deserialize(inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Coll[Byte]]](1).toArray).asInstanceOf[Array[Any]].map(_.asInstanceOf[Byte]))
        ProtoDAOProxyBox(
            ctx,
            Paideia.getConfig(Env.paideiaDaoKey),
            ProtoDAOProxy.contractInstances(Blake2b256(inp.getErgoTree.bytes).array.toList).asInstanceOf[ProtoDAOProxy],
            daoName,
            daoTokenId.toString(),
            inp.getTokens().toArray.foldLeft[Long](0L){(z: Long, token) => z + (if (token.asInstanceOf[ErgoToken].getId()==daoTokenId) token.asInstanceOf[ErgoToken].getValue() else 0L)}
        )
    }
}

