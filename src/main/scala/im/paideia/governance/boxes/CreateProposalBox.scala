package im.paideia.governance.boxes

import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.boxes.PaideiaBox
import im.paideia.governance.contracts.CreateProposal
import im.paideia.util.ConfKeys
import im.paideia.util.Env
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scorex.crypto.hash.Blake2b256
import sigmastate.eval.Colls
import special.collection.Coll
import special.sigma.Box

/** Represents a CreateProposalBox with essential information like context, action boxes
  * and the contract instance.
  *
  * @constructor
  *   create CreateProposalBox
  * @param _ctx
  *   The blockchain context for this box
  * @param proposalBox
  *   The proposal box contained in this CreateProposalBox
  * @param actionBoxes
  *   An array of all the boxes involved in creating the proposal
  * @param voteKey
  *   ID of the vote represented by the CreateProposalBox
  * @param userAddress
  *   User address who created this proposal
  * @param useContract
  *   Contract instance used in creating the proposal
  * @extends
  *   PaideiaBox
  */
final case class CreateProposalBox(
  _ctx: BlockchainContextImpl,
  proposalBox: Box,
  actionBoxes: Array[Box],
  voteKey: String,
  userAddress: Address,
  useContract: CreateProposal
) extends PaideiaBox {

  // Assigning values for some of the properties
  ctx      = _ctx
  contract = useContract.contract
  value = 3000000L + proposalBox.value + actionBoxes.foldLeft(0L) { (z: Long, b: Box) =>
    z + b.value
  }

  /** Returns a list of tokens contained in this CreateProposalBox
    *
    * @return
    *   List of ErgoToken objects contained in this box
    */
  override def tokens: List[ErgoToken] = List(
    new ErgoToken(voteKey, 1L),
    new ErgoToken(
      Env.paideiaTokenId,
      Paideia
        .getConfig(Env.paideiaDaoKey)(
          ConfKeys.im_paideia_fees_createproposal_paideia
        )
        .asInstanceOf[Long] + Env.defaultBotFee
    )
  )

  /** Returns a list of registers contained in this CreateProposalBox
    *
    * @return
    *   List of ErgoValue[_] objects representing registers
    */
  override def registers: List[ErgoValue[_]] = List(
    ErgoValueBuilder.buildFor(Colls.fromArray(userAddress.toPropositionBytes())),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        Array(proposalBox) ++ actionBoxes
      )
    )
  )
}

object CreateProposalBox {

  /** Construct a CreateProposalBox object from an input box.
    *
    * @param ctx
    *   Blockchain context containing context variables.
    * @param inp
    *   Input box to extract data from.
    * @return
    *   A new CreateProposalBox object populated with data extracted from inp.
    */
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): CreateProposalBox = {
    val contract = CreateProposal
      .contractInstances(Blake2b256(inp.getErgoTree().bytes).array.toList)
      .asInstanceOf[CreateProposal]
    val userAddress = Address.fromPropositionBytes(
      ctx.getNetworkType(),
      inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Byte]].toArray
    )
    val boxes   = inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Box]]
    val voteKey = inp.getTokens().get(0).getId().toString()
    CreateProposalBox(
      ctx,
      boxes(0),
      boxes.slice(1, boxes.size).toArray,
      voteKey,
      userAddress,
      contract
    )
  }
}
