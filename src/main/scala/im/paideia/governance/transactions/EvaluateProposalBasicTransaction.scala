package im.paideia.governance.transactions

import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Treasury
import im.paideia.common.filtering._
import im.paideia.common.transactions.PaideiaTransaction
import im.paideia.governance.boxes.ProposalBasicBox
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.util.ConfKeys
import im.paideia.util.Env
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.sdk.ErgoToken
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigma.Coll
import im.paideia.staking.contracts.SplitProfit
import scorex.crypto.authds.ADDigest
import sigma.AvlTree
import org.ergoplatform.appkit.Address
import im.paideia.util.TxTypes

/** This class represents an implementation of a `PaideiaTransaction` used to evaluate the
  * proposal basic transaction.
  *
  * @constructor
  *   Creates a new instance with a given `ctx` representing the blockchain context, `dao`
  *   DAO, `proposalInput` input box, and `_changeAddress`.
  * @param _ctx
  *   Represents the blockchain context
  * @param dao
  *   DAO
  * @param proposalInput
  *   Input box
  * @param _changeAddress
  *   Change address
  */
final case class EvaluateProposalBasicTransaction(
  _ctx: BlockchainContextImpl,
  dao: DAO,
  proposalInput: InputBox,
  _changeAddress: Address
) extends PaideiaTransaction {

  ctx           = _ctx
  changeAddress = _changeAddress

  val proposalInputBox = ProposalBasicBox.fromInputBox(_ctx, proposalInput)

  val paideiaConfigInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      Env.paideiaDaoKey,
      CompareField.ASSET,
      0
    )
  )(0)

  val configInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      dao.key,
      CompareField.ASSET,
      0
    )
  )(0)

  val stakeStateInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid))
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val totalStaked =
    stakeStateInput.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]](1)
  val quorumNeeded    = dao.config[Long](ConfKeys.im_paideia_dao_quorum)
  val thresholdNeeded = dao.config[Long](ConfKeys.im_paideia_dao_threshold)

  val quorumMet = proposalInputBox.totalVotes > (totalStaked * quorumNeeded / 1000)

  val winningVoteAmount = proposalInputBox.voteCount.max
  val winningVoteIndex  = proposalInputBox.voteCount.indexOf(winningVoteAmount)

  val thresholdMet =
    winningVoteAmount > (proposalInputBox.totalVotes * thresholdNeeded / 1000)

  /** Computes the output for the proposal basic transaction.
    *
    * @return
    *   The resulting proposal basic output.
    */
  val proposalBasicOut =
    proposalInputBox.useContract.box(
      _ctx,
      proposalInputBox.name,
      proposalInputBox.proposalIndex,
      proposalInputBox.voteCount,
      proposalInputBox.totalVotes,
      proposalInputBox.endTime,
      if (quorumMet && thresholdMet)
        winningVoteIndex
      else
        -2
    )

  val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)

  val splitProfitOut =
    SplitProfit(ConfKeys.im_paideia_contracts_split_profit, Env.paideiaDaoKey).box(
      ctx,
      1000000L,
      List(
        new ErgoToken(
          Env.paideiaTokenId,
          proposalInputBox.paideiaTokens
        )
      )
    )

  val configDigest =
    ADDigest @@ configInput
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

  val paideiaConfigDigest =
    ADDigest @@ paideiaConfigInput
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

  val context = List(
    ContextVar.of(0.toByte, TxTypes.EVALUATE),
    ContextVar.of(
      1.toByte,
      dao.config.getProof(
        ConfKeys.im_paideia_dao_quorum,
        ConfKeys.im_paideia_dao_threshold
      )(Some(configDigest))
    ),
    ContextVar.of(
      2.toByte,
      paideiaConfig.getProof(
        ConfKeys.im_paideia_fees_createproposal_paideia,
        ConfKeys.im_paideia_contracts_split_profit
      )(Some(paideiaConfigDigest))
    ),
    ContextVar.of(3.toByte, Array[Byte]()),
    ContextVar.of(4.toByte, Array[Byte]()),
    ContextVar
      .of(10.toByte, ErgoValueBuilder.buildFor((winningVoteIndex, winningVoteAmount)))
  )

  /** Sets `inputs`, `dataInputs`, and `outputs` fields of the
    * `EvaluateProposalBasicTransaction` based on the given inputs.
    */
  inputs     = List(proposalInput.withContextVars(context: _*))
  dataInputs = List(configInput, stakeStateInput, paideiaConfigInput)
  outputs    = List(proposalBasicOut.outBox, splitProfitOut.outBox)

  fee = 2000000L
}
