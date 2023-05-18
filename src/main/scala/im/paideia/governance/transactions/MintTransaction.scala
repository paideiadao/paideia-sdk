package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.ErgoAddress
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.util.Env
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.contracts.Mint
import org.ergoplatform.appkit.ContextVar
import im.paideia.DAOConfigKey
import im.paideia.DAOConfigValueSerializer
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.filtering._
import org.ergoplatform.appkit.OutBox
import im.paideia.util.ConfKeys
import im.paideia.DAO
import im.paideia.governance.boxes.ProtoDAOBox
import scorex.crypto.authds.ADDigest
import special.sigma.AvlTree

final case class MintTransaction(
  _ctx: BlockchainContextImpl,
  protoDAOInput: InputBox,
  dao: DAO,
  tokenToMint: DAOConfigKey,
  _changeAddress: ErgoAddress
) extends PaideiaTransaction {
  val protoDAOInputBox = ProtoDAOBox.fromInputBox(_ctx, protoDAOInput)

  val paideiaConfigBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      Env.paideiaDaoKey,
      CompareField.ASSET,
      0
    )
  )(0)
  val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)

  val configDigest =
    ADDigest @@ protoDAOInput
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

  val paideiaConfigDigest =
    ADDigest @@ paideiaConfigBox
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

  val daoName = dao.config[String](ConfKeys.im_paideia_dao_name)

  val tokenInfo = tokenToMint match {
    case ConfKeys.im_paideia_dao_proposal_tokenid =>
      (daoName ++ " Proposal", Long.MaxValue)
    case ConfKeys.im_paideia_dao_vote_tokenid   => (daoName ++ " Vote", Long.MaxValue)
    case ConfKeys.im_paideia_dao_action_tokenid => (daoName ++ " Action", Long.MaxValue)
    case ConfKeys.im_paideia_staking_state_tokenid => (daoName ++ " Stake State", 1L)
  }

  val mintOutput = Mint(
    PaideiaContractSignature(
      networkType = _ctx.getNetworkType(),
      daoKey      = Env.paideiaDaoKey
    )
  ).box(
    _ctx,
    protoDAOInput.getId().toString(),
    tokenInfo._2,
    tokenInfo._1,
    tokenInfo._1,
    0
  )

  var resultingDigest: Option[ADDigest] = None

  val contextVarsProtoDAO = List(
    ContextVar.of(0.toByte, 0.toByte),
    ContextVar.of(
      1.toByte,
      paideiaConfig.getProof(
        ConfKeys.im_paideia_contracts_protodao,
        ConfKeys.im_paideia_contracts_mint
      )(Some(paideiaConfigDigest))
    ),
    ContextVar
      .of(2.toByte, dao.config.getProof("im.paideia.dao.name")(Some(configDigest))),
    ContextVar.of(3.toByte, tokenToMint.ergoValue),
    ContextVar.of(
      4.toByte, {
        val result = dao.config
          .insertProof(
            (
              tokenToMint,
              DAOConfigValueSerializer[Array[Byte]](protoDAOInput.getId().getBytes())
            )
          )(Left(configDigest))
        resultingDigest = Some(result._2)
        result._1
      }
    )
  )

  val protoDAOOutput = ProtoDAO(
    PaideiaContractSignature(
      networkType = _ctx.getNetworkType(),
      daoKey      = Env.paideiaDaoKey
    )
  ).box(
    _ctx,
    dao,
    protoDAOInputBox.stakePool,
    resultingDigest,
    protoDAOInputBox.value - 2000000L
  )
  ctx           = _ctx
  fee           = 1000000
  changeAddress = _changeAddress
  inputs        = List[InputBox](protoDAOInput.withContextVars(contextVarsProtoDAO: _*))
  dataInputs    = List[InputBox](paideiaConfigBox)
  outputs       = List[OutBox](protoDAOOutput.outBox, mintOutput.outBox)
}
