package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.Address
import im.paideia.governance.boxes.CreateProposalBox
import im.paideia.Paideia
import im.paideia.common.filtering._
import im.paideia.util.Env
import org.ergoplatform.appkit.ErgoId
import im.paideia.governance.boxes.DAOOriginBox
import org.ergoplatform.appkit.impl.OutBoxImpl
import sigmastate.eval.CostingSigmaDslBuilder
import special.sigma.Box
import org.ergoplatform.appkit.ErgoToken
import java.nio.charset.StandardCharsets
import org.ergoplatform.appkit.ContextVar
import im.paideia.util.ConfKeys
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.OutBox
import scorex.crypto.authds.ADDigest
import special.sigma.AvlTree

final case class CreateProposalTransaction(
  _ctx: BlockchainContextImpl,
  createProposalInput: InputBox,
  _changeAddress: Address
) extends PaideiaTransaction {
  ctx           = _ctx
  changeAddress = _changeAddress.getErgoAddress()
  fee           = 1000000L

  val createProposalInputBox = CreateProposalBox.fromInputBox(ctx, createProposalInput)

  val dao = Paideia.getDAO(createProposalInputBox.useContract.contractSignature.daoKey)

  val daoOriginInput = Paideia.getBox(
    new FilterNode(
      FilterType.FTALL,
      List(
        new FilterLeaf(
          FilterType.FTEQ,
          Env.daoTokenId,
          CompareField.ASSET,
          0
        ),
        new FilterLeaf(
          FilterType.FTEQ,
          ErgoId.create(dao.key).getBytes().toIterable,
          CompareField.REGISTER,
          0
        )
      )
    )
  )(0)

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

  val daoOriginInputBox = DAOOriginBox.fromInput(ctx, daoOriginInput)

  val daoOriginOutput = DAOOriginBox(
    ctx,
    dao,
    daoOriginInputBox.propTokens - 1L,
    daoOriginInputBox.voteTokens,
    daoOriginInputBox.actionTokens - createProposalInputBox.actionBoxes.size.toLong,
    daoOriginInputBox.useContract
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

  val proposalOutput = new OutBoxImpl(
    (new CostingSigmaDslBuilder())
      .toErgoBox(createProposalInputBox.proposalBox)
      .toCandidate
  )

  val actionOutputs = createProposalInputBox.actionBoxes.map { (b: Box) =>
    new OutBoxImpl((new CostingSigmaDslBuilder()).toErgoBox(b).toCandidate)
  }.toList

  val userOutput = ctx
    .newTxBuilder()
    .outBoxBuilder()
    .contract(createProposalInputBox.userAddress.toErgoContract())
    .value(1000000L)
    .tokens(new ErgoToken(createProposalInputBox.voteKey, 1L))
    .build()

  val daoOriginContext = List(
    ContextVar.of(
      0.toByte,
      Paideia
        .getConfig(Env.paideiaDaoKey)
        .getProof(
          ConfKeys.im_paideia_contracts_dao,
          ConfKeys.im_paideia_fees_createproposal_paideia
        )(Some(paideiaConfigDigest))
    ),
    ContextVar.of(
      1.toByte,
      dao.config.getProof(
        List(
          ConfKeys.im_paideia_contracts_proposal(proposalOutput.getErgoTree().bytes)
        ) ++
          actionOutputs.map((ao: OutBox) =>
            ConfKeys.im_paideia_contracts_action(ao.getErgoTree().bytes)
          ): _*
      )(Some(configDigest))
    )
  )

  inputs = List(daoOriginInput.withContextVars(daoOriginContext: _*), createProposalInput)
  dataInputs = List(paideiaConfigInput, configInput)
  outputs = List(daoOriginOutput.outBox, proposalOutput) ++ actionOutputs ++ List(
    userOutput
  )
}
