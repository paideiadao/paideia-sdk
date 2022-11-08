package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.boxes.VoteBox
import im.paideia.Paideia
import org.ergoplatform.appkit.impl.BlockchainContextImpl

class Vote(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, voteKey: String, stakeKey: String, releaseTime: Long): VoteBox = {
        VoteBox(ctx,Paideia.getConfig(contractSignature.daoKey),voteKey,stakeKey,releaseTime,this)
    }
}

object Vote extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): Vote = {
        getContractInstance[Vote](contractSignature = contractSignature,new Vote(contractSignature))
    }
}