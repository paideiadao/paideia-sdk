package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAO
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigmastate.eval.Colls
import im.paideia.governance.contracts.ActionSendFundsBasic
import special.sigma.Box
import im.paideia.Paideia
import org.ergoplatform.appkit.InputBox
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import scala.collection.mutable.HashMap

final case class ActionSendFundsBasicBox(
    _ctx: BlockchainContextImpl,
    dao: DAO,
    proposalId: Int,
    optionId: Int,
    repeats: Int,
    activationTime: Long,
    repeatDelay: Long,
    outputs: Array[Box],
    useContract: ActionSendFundsBasic) extends PaideiaBox 
{
    ctx = _ctx
    value = 10000000L
    contract = useContract.contract

    override def tokens: List[ErgoToken] = List(
        new ErgoToken(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid),1L)
    )

    override def registers: List[ErgoValue[_]] = List(
        ErgoValueBuilder.buildFor(Colls.fromArray(Array(
            proposalId.toLong,
            optionId.toLong,
            repeats,
            activationTime,
            repeatDelay))),
        ErgoValueBuilder.buildFor(Colls.fromArray(outputs))
    )

    def fundsNeeded: (Long,Array[ErgoToken]) = {
        val nanoErgsNeeded = outputs.foldLeft(0L){(z: Long, b: Box) => z+b.value}
        val tokensNeeded = HashMap[Coll[Byte],Long]()
        outputs.foreach(_.tokens.toArray.foreach((token: (Coll[Byte],Long)) => 
            if (tokensNeeded.contains(token._1)) 
                tokensNeeded(token._1)+=token._2 
            else 
                tokensNeeded(token._1) = token._2))
        (
            nanoErgsNeeded,
            tokensNeeded.map((kv: (Coll[Byte],Long)) => new ErgoToken(kv._1.toArray,kv._2)).toArray
        )
    }
}

object ActionSendFundsBasicBox {
    def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ActionSendFundsBasicBox = {
        val contract = ActionSendFundsBasic.contractInstances(Blake2b256(inp.getErgoTree().bytes).array.toList).asInstanceOf[ActionSendFundsBasic]
        val params = inp.getRegisters().get(0).getValue.asInstanceOf[Coll[Long]]
        ActionSendFundsBasicBox(
            ctx,
            Paideia.getDAO(contract.contractSignature.daoKey),
            params(0).toInt,
            params(1).toInt,
            params(2).toInt,
            params(3),
            params(4),
            inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Box]].toArray,
            contract)
    }
}
