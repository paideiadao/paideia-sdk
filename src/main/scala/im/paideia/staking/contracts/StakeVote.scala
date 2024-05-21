package im.paideia.staking.contracts

import scorex.crypto.authds.ADDigest
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import scala.collection.mutable.HashMap
import org.ergoplatform.sdk.ErgoId
import im.paideia.util.ConfKeys
import scorex.crypto.authds.ADDigest
import im.paideia.Paideia
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.boxes.StakeVoteBox
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant

class StakeVote(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(ctx: BlockchainContextImpl) = StakeVoteBox(ctx, this)

  override lazy val parameters: Map[String, Constant[SType]] = {
    val cons = new HashMap[String, Constant[SType]]()
    cons.put(
      "imPaideiaDaoKey",
      ByteArrayConstant(ErgoId.create(contractSignature.daoKey).getBytes)
    )
    cons.toMap
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_STAKING_STATE_TOKEN_ID",
      ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_VOTE",
      ConfKeys.im_paideia_contracts_staking_vote.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_PROPOSAL_TOKEN_ID",
      ConfKeys.im_paideia_dao_proposal_tokenid.ergoValue.getValue()
    )
    cons
  }

  override def getConfigContext(configDigest: Option[ADDigest]) = Paideia
    .getConfig(contractSignature.daoKey)
    .getProof(
      ConfKeys.im_paideia_contracts_staking_vote,
      ConfKeys.im_paideia_staking_state_tokenid,
      ConfKeys.im_paideia_dao_proposal_tokenid
    )(configDigest)
}

object StakeVote extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): StakeVote =
    getContractInstance[StakeVote](
      contractSignature,
      new StakeVote(contractSignature)
    )
}
