package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaActor
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.boxes.CastVoteBox
import im.paideia.governance.VoteRecord
import org.ergoplatform.appkit.Address
import im.paideia.governance.transactions.CastVoteTransaction
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.Paideia
import im.paideia.util.Env
import java.util.HashMap
import im.paideia.util.ConfKeys

class CastVote(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    voteKey: String,
    proposalIndex: Int,
    vote: VoteRecord,
    userAddress: Address
  ): CastVoteBox = {
    CastVoteBox(ctx, voteKey, proposalIndex, vote, userAddress, this)
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_DAO_PROPOSAL_TOKENID",
      Paideia
        .getConfig(contractSignature.daoKey)
        .getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid)
    )
    cons.put(
      "_IM_PAIDEIA_DAO_VOTE_TOKENID",
      Paideia
        .getConfig(contractSignature.daoKey)
        .getArray[Byte](ConfKeys.im_paideia_dao_vote_tokenid)
    )
    cons
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case te: TransactionEvent => {
        PaideiaEventResponse.merge(
          te.tx
            .getOutputs()
            .asScala
            .map { (eto: ErgoTransactionOutput) =>
              {
                if (eto.getErgoTree() == ergoTree.bytesHex) {
                  PaideiaEventResponse(
                    1,
                    List(
                      CastVoteTransaction(
                        te._ctx,
                        new InputBoxImpl(eto),
                        Paideia.getDAO(contractSignature.daoKey),
                        Address.create(Env.operatorAddress)
                      )
                    )
                  )
                } else {
                  PaideiaEventResponse(0)
                }
              }
            }
            .toList
        )
      }
      case _ => PaideiaEventResponse(0)
    }
    val superResponse = super.handleEvent(event)
    response
  }
}

object CastVote extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): CastVote =
    getContractInstance(contractSignature, new CastVote(contractSignature))
}
