package im.paideia.governance.contracts

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ContextVar
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.InputBox
import im.paideia.governance.VoteRecord
import scorex.crypto.authds.ADDigest

abstract trait ProposalContract {

  def castVote(
    ctx: BlockchainContextImpl,
    inputBox: InputBox,
    vote: VoteRecord,
    voteKey: String,
    digestOrHeight: Either[ADDigest, Int]
  ): (List[ContextVar], PaideiaBox)
}
