package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import im.paideia.DAO
import org.ergoplatform.ErgoAddress
import im.paideia.governance.boxes.CreateVoteProxyBox
import im.paideia.governance.boxes.DAOOriginBox
import im.paideia.governance.contracts.Vote
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.common.filtering.FilterNode
import im.paideia.common.filtering.FilterType
import im.paideia.common.filtering.FilterLeaf
import im.paideia.util.Env
import im.paideia.common.filtering.CompareField
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ErgoValue
import scala.math.Ordering.Implicits
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.ContextVar
import im.paideia.staking.TotalStakingState
import scorex.crypto.authds.ADDigest
import special.sigma.AvlTree

case class CreateVoteTransaction(
  _ctx: BlockchainContextImpl,
  createVoteProxyInput: InputBox,
  dao: DAO,
  _changeAddress: ErgoAddress
) extends PaideiaTransaction {
  ctx           = _ctx
  changeAddress = _changeAddress

  val daoOriginInputs = Paideia.getBox(
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
  )

  val daoOriginInput = daoOriginInputs(0)

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

  val daoOriginInputBox  = DAOOriginBox.fromInput(ctx, daoOriginInput)
  val createVoteProxyBox = CreateVoteProxyBox.fromInputBox(ctx, createVoteProxyInput)

  val voteKey = daoOriginInput.getId().toString()

  val voteContract = Vote(PaideiaContractSignature(daoKey = dao.key))
  val voteOutput   = voteContract.box(ctx, voteKey, createVoteProxyBox.stakeKey, 0L)

  val daoOriginContract = DAOOrigin(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))

  val daoOriginOutput = daoOriginContract.box(
    ctx,
    dao,
    daoOriginInputBox.propTokens,
    daoOriginInputBox.voteTokens - 1,
    daoOriginInputBox.actionTokens
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

  val userOutput = ctx
    .newTxBuilder()
    .outBoxBuilder()
    .mintToken(
      new Eip4Token(
        voteKey,
        1L,
        dao.config[String](ConfKeys.im_paideia_dao_name) ++ " Vote Key",
        dao.config[String](ConfKeys.im_paideia_dao_name) ++ " Vote Key",
        0
      )
    )
    .contract(createVoteProxyBox.userAddress.toErgoContract())
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
        ConfKeys.im_paideia_contracts_vote,
        ConfKeys.im_paideia_staking_state_tokenid
      )(Some(configDigest))
    ),
    ContextVar.of(
      2.toByte,
      TotalStakingState(dao.key).currentStakingState
        .getStakes(List(createVoteProxyBox.stakeKey))
        .proof
        .bytes
    )
  )

  val createVoteProxyContext =
    ContextVar.of(
      0.toByte,
      dao.config.getProof(ConfKeys.im_paideia_dao_name)(Some(configDigest))
    )

  inputs = List(
    daoOriginInput.withContextVars(daoOriginContext: _*),
    createVoteProxyInput.withContextVars(createVoteProxyContext)
  )
  dataInputs = List(paideiaConfigInput, configInput, stakeStateInput)
  outputs    = List(daoOriginOutput.outBox, voteOutput.outBox, userOutput)

  fee = 1000000L

}
