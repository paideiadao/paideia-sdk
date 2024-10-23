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
import scala.collection.mutable.HashMap
import im.paideia.util.ConfKeys
import scorex.crypto.hash.Blake2b256
import sigma.AvlTree
import scorex.crypto.authds.ADDigest
import im.paideia.common.events.CreateTransactionsEvent
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.transactions.RefundTransaction
import sigma.Coll
import sigma.ast.ConstantPlaceholder
import sigma.ast.SCollection
import sigma.ast.SByte
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import sigma.Colls
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfigKey

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

  override lazy val parameters: Map[String, Constant[SType]] = {
    val params = new scala.collection.mutable.HashMap[String, Constant[SType]]()
    params.put(
      "imPaideiaDaoProposalTokenId",
      ByteArrayConstant(
        Colls.fromArray(
          Paideia
            .getConfig(contractSignature.daoKey)
            .getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid)
        )
      )
    )
    params.toMap
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      val b = CastVoteBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
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
                val castVoteBox = CastVoteBox.fromInputBox(cte.ctx, boxes(b))
                val unsigned = CastVoteTransaction(
                  cte.ctx,
                  castVoteBox.proposalIndex,
                  castVoteBox.stakeKey,
                  castVoteBox.vote,
                  Paideia.getDAO(contractSignature.daoKey),
                  Address.create(Env.operatorAddress),
                  castVoteBox.userAddress
                )
                val check = unsigned.fundsMissing()
                unsigned.userInputs = List(boxes(b))
                unsigned
              }
            )
          )
        })
      case _ => PaideiaEventResponse(0)
    }
    PaideiaEventResponse.merge(List(super.handleEvent(event), response))
  }
}

object CastVote extends PaideiaActor {
  override def apply(
    configKey: DAOConfigKey,
    daoKey: String,
    digest: Option[ADDigest] = None
  ): CastVote =
    contractFromConfig(configKey, daoKey, digest)
  override def apply(contractSignature: PaideiaContractSignature): CastVote =
    getContractInstance(contractSignature, new CastVote(contractSignature))
}
