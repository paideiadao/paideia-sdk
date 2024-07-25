package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.Address
import im.paideia.governance.boxes.CreateProposalBox
import im.paideia.Paideia
import im.paideia.common.filtering._
import im.paideia.util.Env
import org.ergoplatform.sdk.ErgoId
import im.paideia.governance.boxes.DAOOriginBox
import org.ergoplatform.appkit.impl.OutBoxImpl
import sigma.Box
import org.ergoplatform.sdk.ErgoToken
import java.nio.charset.StandardCharsets
import org.ergoplatform.appkit.ContextVar
import im.paideia.util.ConfKeys
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.OutBox
import scorex.crypto.authds.ADDigest
import sigma.AvlTree
import sigma.data.CBox
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigma.Colls
import scorex.util.encode.Base16
import im.paideia.staking.boxes.StakeStateBox
import im.paideia.util.TxTypes
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.common.contracts.PaideiaContractSignature

final case class CreateProposalTransaction(
  _ctx: BlockchainContextImpl,
  daoKey: String,
  proposalBox: Box,
  actionBoxes: Array[Box],
  voteKey: String,
  _changeAddress: Address,
  userAddress: Address
) extends PaideiaTransaction {
  ctx           = _ctx
  changeAddress = _changeAddress
  fee           = 1850000L

  val dao = Paideia.getDAO(daoKey)

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
          DAOOrigin(ConfKeys.im_paideia_contracts_dao, dao.key).ergoTree.bytesHex,
          CompareField.ERGO_TREE,
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

  val stakeStateInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      Base16.encode(dao.config.getArray(ConfKeys.im_paideia_staking_state_tokenid)),
      CompareField.ASSET,
      0
    )
  )(0)

  val stakeStateBox = StakeStateBox.fromInputBox(ctx, stakeStateInput)

  val daoOriginInputBox = DAOOriginBox.fromInputBox(ctx, daoOriginInput)

  val daoOriginOutput = DAOOriginBox(
    ctx,
    dao,
    daoOriginInputBox.propTokens - 1L,
    daoOriginInputBox.actionTokens - actionBoxes.size.toLong,
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
    proposalBox.asInstanceOf[CBox].ebox
  )

  val actionOutputs = actionBoxes.map { (b: Box) =>
    new OutBoxImpl(b.asInstanceOf[CBox].ebox)
  }.toList

  val userOutput = ctx
    .newTxBuilder()
    .outBoxBuilder()
    .contract(userAddress.toErgoContract())
    .value(1000000L)
    .tokens(new ErgoToken(voteKey, 1L))
    .build()

  val daoOriginContext = List(
    ContextVar.of(0.toByte, TxTypes.CREATE_PROPOSAL),
    ContextVar.of(
      1.toByte,
      Paideia
        .getConfig(Env.paideiaDaoKey)
        .getProof(
          ConfKeys.im_paideia_fees_createproposal_paideia
        )(Some(paideiaConfigDigest))
    ),
    ContextVar.of(
      2.toByte,
      dao.config.getProof(
        List(
          ConfKeys.im_paideia_contracts_dao,
          ConfKeys.im_paideia_dao_min_proposal_time,
          ConfKeys.im_paideia_dao_min_stake_proposal,
          ConfKeys.im_paideia_contracts_proposal(proposalOutput.getErgoTree().bytes)
        ) ++
          actionOutputs.map((ao: OutBox) =>
            ConfKeys.im_paideia_contracts_action(ao.getErgoTree().bytes)
          ): _*
      )(Some(configDigest))
    ),
    ContextVar.of(
      3.toByte,
      ErgoValueBuilder.buildFor(proposalBox)
    ),
    ContextVar.of(
      4.toByte,
      ErgoValueBuilder.buildFor(Colls.fromArray(actionBoxes))
    ),
    ContextVar.of(
      5.toByte,
      Base16.decode(voteKey).get
    ),
    ContextVar.of(
      6.toByte,
      TotalStakingState(dao.key).currentStakingState
        .getStakes(List(voteKey), Some(stakeStateBox.stateDigest))
        .proof
        .bytes
    )
  )

  inputs     = List(daoOriginInput.withContextVars(daoOriginContext: _*))
  dataInputs = List(paideiaConfigInput, configInput, stakeStateInput)
  outputs = List(daoOriginOutput.outBox, proposalOutput) ++ actionOutputs ++ List(
    userOutput
  )
}
