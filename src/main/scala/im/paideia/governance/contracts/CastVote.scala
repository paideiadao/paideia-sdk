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
import scorex.crypto.hash.Blake2b256
import special.sigma.AvlTree
import scorex.crypto.authds.ADDigest
import im.paideia.common.events.CreateTransactionsEvent
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.transactions.RefundTransaction
import special.collection.Coll

class CastVote(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    stakeKey: String,
    proposalIndex: Int,
    vote: VoteRecord,
    userAddress: Address
  ): CastVoteBox = {
    CastVoteBox(ctx, stakeKey, proposalIndex, vote, userAddress, this)
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

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val boxSet = getUtxoSet
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent =>
        PaideiaEventResponse.merge(boxSet.toList.map { b =>
          PaideiaEventResponse(
            1,
            List(
              if (boxes(b).getCreationHeight() < cte.height - 30) {
                RefundTransaction(
                  cte.ctx,
                  boxes(b),
                  Address.fromPropositionBytes(
                    NetworkType.MAINNET,
                    boxes(b)
                      .getRegisters()
                      .get(2)
                      .getValue()
                      .asInstanceOf[Coll[Byte]]
                      .toArray
                  )
                )
              } else {
                CastVoteTransaction(
                  cte.ctx,
                  boxes(b),
                  Paideia.getDAO(contractSignature.daoKey),
                  Address.create(Env.operatorAddress)
                )
              }
            )
          )
        })
      case te: TransactionEvent =>
        PaideiaEventResponse.merge(
          te.tx
            .getInputs()
            .asScala
            .map(eti =>
              if (boxSet.contains(eti.getBoxId())) {
                val castVoteBox = CastVoteBox.fromInputBox(te.ctx, boxes(eti.getBoxId()))
                val proposalContract = Paideia
                  .getProposalContract(
                    Blake2b256(
                      new InputBoxImpl(te.tx.getOutputs().get(2)).getErgoTree().bytes
                    ).array.toList
                  )
                val proposalBox = proposalContract
                  .asInstanceOf[PaideiaContract]
                  .boxes(te.tx.getInputs().get(2).getBoxId())
                proposalContract.castVote(
                  te.ctx,
                  proposalBox,
                  castVoteBox.vote,
                  castVoteBox.stakeKey,
                  if (te.mempool)
                    Left(
                      ADDigest @@ proposalBox
                        .getRegisters()
                        .get(2)
                        .getValue()
                        .asInstanceOf[AvlTree]
                        .digest
                        .toArray
                    )
                  else Right(te.height)
                )
                PaideiaEventResponse(2)
              } else {
                PaideiaEventResponse(0)
              }
            )
        )
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
