package im.paideia.governance.contracts

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ContextVar
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.InputBox
import im.paideia.governance.VoteRecord
import scorex.crypto.authds.ADDigest
import work.lithos.plasma.collections.ProvenResult
import sigma.Coll

abstract trait ProposalContract {

  def castVote(
    ctx: BlockchainContextImpl,
    inputBox: InputBox,
    vote: VoteRecord,
    voteKey: String,
    digestOrHeight: Either[ADDigest, Int]
  ): (List[Coll[Byte]], PaideiaBox)

  def getVote(
    voteKey: String,
    proposalIndex: Int,
    digestOrHeight: Either[ADDigest, Int]
  ): ProvenResult[VoteRecord]
}
