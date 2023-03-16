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
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import special.collection.Coll
import im.paideia.staking.contracts.SplitProfit

/**
  * This class represents an implementation of a `PaideiaTransaction`
  * used to evaluate the proposal basic transaction.
  *
  * @constructor Creates a new instance with a given `ctx` representing the blockchain context,
  *              `dao` DAO, `proposalInput` input box, and `_changeAddress`.
  * @param _ctx           Represents the blockchain context
  * @param dao            DAO
  * @param proposalInput  Input box
  * @param _changeAddress Change address
  */
final case class EvaluateProposalBasicTransaction(
  _ctx: BlockchainContextImpl,
  dao: DAO,
  proposalInput: InputBox,
  _changeAddress: ErgoAddress
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
    stakeStateInput.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]](2)
  val quorumNeeded = dao.config[Long](ConfKeys.im_paideia_dao_quorum)

  val quorumMet = proposalInputBox.totalVotes > (totalStaked * quorumNeeded / 1000)

  val winningVoteAmount = proposalInputBox.voteCount.max
  val winningVoteIndex  = proposalInputBox.voteCount.indexOf(winningVoteAmount)

  /**
    * Computes the output for the proposal basic transaction.
    *
    * @return The resulting proposal basic output.
    */
  val proposalBasicOut = ProposalBasic(PaideiaContractSignature(daoKey = dao.key)).box(
    _ctx,
    proposalInputBox.proposalIndex,
    proposalInputBox.voteCount,
    proposalInputBox.totalVotes,
    proposalInputBox.endTime,
    if (quorumMet)
      winningVoteIndex
    else
      -2
  )

  val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)

  val paideiaSplitProfitContractSig = paideiaConfig[PaideiaContractSignature](
    ConfKeys.im_paideia_contracts_split_profit
  ).withDaoKey(Env.paideiaDaoKey)

  val splitProfitOut = SplitProfit(paideiaSplitProfitContractSig).box(
    ctx,
    1000000L,
    List(
      new ErgoToken(
        Env.paideiaTokenId,
        paideiaConfig(ConfKeys.im_paideia_fees_createproposal_paideia)
      )
    )
  )

  val context = List(
    ContextVar.of(
      0.toByte,
      dao.config.getProof(
        ConfKeys.im_paideia_dao_quorum,
        ConfKeys.im_paideia_staking_state_tokenid
      )
    ),
    ContextVar.of(
      1.toByte,
      paideiaConfig.getProof(
        ConfKeys.im_paideia_fees_createproposal_paideia,
        ConfKeys.im_paideia_contracts_split_profit
      )
    ),
    ContextVar.of(2.toByte, Array[Byte]()),
    ContextVar.of(3.toByte, Array[Byte]()),
    ContextVar
      .of(4.toByte, ErgoValueBuilder.buildFor((winningVoteIndex, winningVoteAmount)))
  )

  /**
    * Sets `inputs`, `dataInputs`, and `outputs` fields of the `EvaluateProposalBasicTransaction`
    * based on the given inputs.
    */
  inputs     = List(proposalInput.withContextVars(context: _*))
  dataInputs = List(configInput, stakeStateInput, paideiaConfigInput)
  outputs    = List(proposalBasicOut.outBox, splitProfitOut.outBox)

  fee = 1000000L
}
