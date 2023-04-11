package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.boxes.VoteBox
import im.paideia.Paideia
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.ConfKeys
import java.util.HashMap

class Vote(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {
  def box(
    ctx: BlockchainContextImpl,
    voteKey: String,
    stakeKey: String,
    releaseTime: Long
  ): VoteBox = {
    VoteBox(
      ctx,
      Paideia.getConfig(contractSignature.daoKey),
      voteKey,
      stakeKey,
      releaseTime,
      this
    )
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_DAO_PROPOSAL_TOKENID",
      Paideia
        .getConfig(contractSignature.daoKey)
        .getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid)
    )
    cons
  }
}

object Vote extends PaideiaActor {
  override def apply(contractSignature: PaideiaContractSignature): Vote = {
    getContractInstance[Vote](contractSignature, new Vote(contractSignature))
  }
}
