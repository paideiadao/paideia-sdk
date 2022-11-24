package im.paideia.common.contracts

import org.ergoplatform.appkit.ErgoToken
import im.paideia.common.boxes.TreasuryBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import java.util.HashMap
import im.paideia.Paideia
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.InputBox

class Treasury(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig, value: Long, tokens: List[ErgoToken]): TreasuryBox = {
        val res = new TreasuryBox
        res.ctx = ctx
        res.contract = contract
        res.value = value
        res.tokens = tokens
        res
    }

    override lazy val constants: HashMap[String,Object] = {
        val cons = new HashMap[String,Object]()
        cons.put("_IM_PAIDEIA_DAO_ACTION_TOKENID",Paideia.getConfig(contractSignature.daoKey).getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid))
        cons
    }

    def findBoxes(nanoErgNeeded: Long, tokensNeeded: Array[ErgoToken]): Option[Array[InputBox]] = {
        var assetsFound = false
        var nanoErgFound = 0L
        var tokensFound = new HashMap[String,Long]()
        var result = List[InputBox]()
        boxes.foreach(
            (box: (String,InputBox)) => {
                if (!assetsFound || result.length < 20) {
                    result = result.::(box._2)
                    nanoErgFound += box._2.getValue()
                    box._2.getTokens().forEach(
                        (token: ErgoToken) => 
                            tokensFound.put(token.getId().toString(),token.getValue()+tokensFound.getOrDefault(token.getId().toString(),0L))
                    )
                    assetsFound = nanoErgFound >= nanoErgNeeded && tokensNeeded.forall(
                        (token: ErgoToken) =>
                            token.getValue <= tokensFound.getOrDefault(token.getId().toString(),0L)
                    )
                } else Unit
            }
        )
        if (result.length>0) {
            Some(result.toArray)
        } else {
            None
        }
    }
}

object Treasury extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): Treasury = 
            getContractInstance[Treasury](contractSignature,new Treasury(contractSignature))
}
