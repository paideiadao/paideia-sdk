package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import im.paideia.governance.VoteRecord
import im.paideia.governance.contracts.CastVote
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scorex.crypto.hash.Blake2b256
import sigmastate.eval.Colls
import special.collection.Coll
import im.paideia.util.Env

/** A box used for casting a vote by a user, extending PaideiaBox class.
  *
  * @param _ctx
  *   Blockchain context implementation object
  * @param voteKey
  *   Uniquely identifies the vote
  * @param proposalIndex
  *   Index of the proposal being voted on
  * @param vote
  *   VoteRecord object representing the result the user is voting for
  * @param userAddress
  *   Address of the user who is casting the vote
  * @param useContract
  *   CastVote contract instance containing the rules governing this type of box
  */
final case class CastVoteBox(
  _ctx: BlockchainContextImpl,
  voteKey: String,
  proposalIndex: Int,
  vote: VoteRecord,
  userAddress: Address,
  useContract: CastVote
) extends PaideiaBox {
  ctx      = _ctx
  contract = useContract.contract
  value    = 3000000L

  /** Returns the list of tokens associated with the box, which includes a single
    * ErgoToken identified by 'voteKey' and with a quantity of 1.
    *
    * @return
    *   List of ErgoTokens associated with the box
    */
  override def tokens: List[ErgoToken] = {
    List(
      new ErgoToken(voteKey, 1L),
      new ErgoToken(Env.paideiaTokenId, Env.defaultBotFee)
    )
  }

  /** Returns the list of registers associated with the box, which includes
    * ErgoValueBuilders for the following items:
    *   - 'proposalIndex', representing the index of the proposal being voted on
    *   - 'vote', an ErgoValueBuilder for the
    *     Colls.fromArray(VoteRecord.convertsVoteRecord.convertToBytes(vote))
    *   - 'userAddress', an ErgoValueBuilder for the serialized bytes of the user's
    *     address
    *
    * @return
    *   List of ErgoValueBuilders corresponding to the box's registers
    */
  override def registers: List[ErgoValue[_]] = {
    List(
      ErgoValueBuilder.buildFor(proposalIndex),
      ErgoValueBuilder.buildFor(
        Colls.fromArray(VoteRecord.convertsVoteRecord.convertToBytes(vote))
      ),
      ErgoValueBuilder.buildFor(Colls.fromArray(userAddress.toPropositionBytes()))
    )
  }
}

/** A box to hold casted votes.
  */
object CastVoteBox {

  /** Create a CastVoteBox from an InputBox and Context.
    *
    * @param ctx
    *   the current blockchain context
    * @param inp
    *   the input box representing the cast vote
    * @return
    *   a new instance of CastVoteBox
    */
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): CastVoteBox = {
    val contract = CastVote
      .contractInstances(Blake2b256(inp.getErgoTree().bytes).array.toList)
      .asInstanceOf[CastVote]
    CastVoteBox(
      ctx,
      inp.getTokens().get(0).getId().toString(),
      inp.getRegisters().get(0).getValue().asInstanceOf[Int],
      VoteRecord.convertsVoteRecord.convertFromBytes(
        inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Byte]].toArray
      ),
      Address.fromPropositionBytes(
        ctx.getNetworkType(),
        inp.getRegisters().get(2).getValue().asInstanceOf[Coll[Byte]].toArray
      ),
      contract
    )
  }
}
