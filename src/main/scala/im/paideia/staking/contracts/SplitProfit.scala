package im.paideia.staking.contracts

import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.staking.boxes.SplitProfitBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.common.BlockEvent
import im.paideia.common.PaideiaEvent
import im.paideia.common.PaideiaEventResponse
import im.paideia.staking.transactions.SplitProfitTransaction
import im.paideia.Paideia

class SplitProfit(contractSig: PaideiaContractSignature) extends PaideiaContract(contractSig) {

    private var lastProfitSplit: Int = 0

    def box(ctx: BlockchainContextImpl, value: Long, tokens: List[ErgoToken]): SplitProfitBox = {
        SplitProfitBox(ctx, value, tokens, this)
    }

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        val response: PaideiaEventResponse = event match {
            case be: BlockEvent => {
                if ((be.block.getHeader().getHeight() > lastProfitSplit + 0) || boxes.size > 50) {
                    lastProfitSplit = be.block.getHeader().getHeight()
                    if (boxes.size > 0) {
                        PaideiaEventResponse(1,List(SplitProfitTransaction(
                            be.ctx,
                            boxes.values.take(50).toList,
                            Paideia.getDAO(contractSignature.daoKey))))
                    } else {
                        PaideiaEventResponse(0)
                    }
                } else {
                    PaideiaEventResponse(0)
                }
            }
            case _ => PaideiaEventResponse(0)
        }
        val superResponse = super.handleEvent(event)
        response
    }
}

object SplitProfit extends PaideiaActor {
   override def apply(contractSignature: PaideiaContractSignature): SplitProfit = getContractInstance[SplitProfit](contractSignature,new SplitProfit(contractSignature))
}