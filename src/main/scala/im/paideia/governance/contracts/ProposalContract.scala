package im.paideia.governance.contracts

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ContextVar
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.InputBox
import im.paideia.governance.VoteRecord

abstract trait ProposalContract {
    def castVote(ctx: BlockchainContextImpl, inputBox: InputBox, vote: VoteRecord, voteKey: String): (List[ContextVar],PaideiaBox)
}
