package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.CompareField
import im.paideia.common.filtering.FilterType
import im.paideia.Paideia
import im.paideia.util.Env
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.contracts.Treasury
import org.ergoplatform.sdk.ErgoToken
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.ErgoAddress
import im.paideia.governance.contracts.PaideiaOrigin
import org.ergoplatform.appkit.ContextVar
import im.paideia.DAOConfig
import im.paideia.DAO
import special.collection.Coll
import im.paideia.DAOConfigValueSerializer
import im.paideia.governance.contracts.Mint
import im.paideia.DAOConfigValueDeserializer
import im.paideia.governance.boxes.ProtoDAOProxyBox
import org.ergoplatform.sdk.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.staking.contracts.SplitProfit
import work.lithos.plasma.collections.PlasmaMap
import sigmastate.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import im.paideia.DAOConfigKey
import scorex.crypto.authds.ADDigest
import special.sigma.AvlTree
import org.ergoplatform.appkit.Address

case class CreateProtoDAOTransaction(
  _ctx: BlockchainContextImpl,
  protoDAOProxyInput: InputBox,
  _changeAddress: Address
) extends PaideiaTransaction {
  val protoDAOProxyInputBox = ProtoDAOProxyBox.fromInputBox(_ctx, protoDAOProxyInput)
  val paideiaConfig         = Paideia.getConfig(Env.paideiaDaoKey)
  val ergFee                = paideiaConfig[Long]("im.paideia.fees.createdao.erg")
  val paideiaFee            = paideiaConfig[Long]("im.paideia.fees.createdao.paideia")

  val paideiaConfigBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      Env.paideiaDaoKey,
      CompareField.ASSET,
      0
    )
  )(0)
  val newDAOConfig = DAOConfig(protoDAOProxyInput.getId().toString())
  val newDAO       = new DAO(protoDAOProxyInput.getId().toString(), newDAOConfig)
  Paideia.addDAO(newDAO)

  val paideiaOriginInput = Paideia.getBox(
    new FilterLeaf[String](FilterType.FTEQ, Env.paideiaOriginNFT, CompareField.ASSET, 0)
  )(0)

  val paideiaOriginOutput = PaideiaOrigin(
    PaideiaContractSignature(
      networkType = _ctx.getNetworkType(),
      daoKey      = Env.paideiaDaoKey
    )
  ).box(_ctx, paideiaConfig, paideiaOriginInput.getTokens().get(1).getValue - 1L)

  val paideiaSplitProfitContractSig = paideiaConfig[PaideiaContractSignature](
    ConfKeys.im_paideia_contracts_split_profit
  ).withDaoKey(Env.paideiaDaoKey)
  val paideiaSplitProfitContract = SplitProfit(paideiaSplitProfitContractSig)

  val paideiaSplitProfitOutput = paideiaSplitProfitContract.box(
    _ctx,
    ergFee + 1000000L,
    List(new ErgoToken(Env.paideiaTokenId, paideiaFee))
  )

  val mintOutput = Mint(
    PaideiaContractSignature(
      networkType = _ctx.getNetworkType(),
      daoKey      = Env.paideiaDaoKey
    )
  ).box(
    _ctx,
    protoDAOProxyInput.getId().toString(),
    1L,
    protoDAOProxyInputBox.daoName ++ " DAO Key",
    protoDAOProxyInputBox.daoName ++ " DAO Key",
    0
  )

  val paideiaConfigDigest =
    ADDigest @@ paideiaConfigBox
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

  val contextVarPaideiaOrigin = ContextVar.of(
    0.toByte,
    paideiaConfig.getProof(
      ConfKeys.im_paideia_fees_createdao_erg,
      ConfKeys.im_paideia_fees_createdao_paideia,
      ConfKeys.im_paideia_contracts_protodao,
      ConfKeys.im_paideia_contracts_protodaoproxy,
      ConfKeys.im_paideia_contracts_split_profit
    )(Some(paideiaConfigDigest))
  )

  var resultingDigest: Option[ADDigest] = None

  val contextVarsProtoDAOProxy = List(
    ContextVar.of(
      0.toByte,
      paideiaConfig.getProof(
        "im.paideia.contracts.protodao",
        "im.paideia.contracts.mint"
      )(Some(paideiaConfigDigest))
    ),
    ContextVar.of(1.toByte, newDAOConfig._config.ergoValue()),
    ContextVar.of(
      2.toByte, {
        val result = newDAOConfig
          .insertProof(
            (
              ConfKeys.im_paideia_dao_name,
              DAOConfigValueSerializer(protoDAOProxyInputBox.daoName)
            ),
            (
              ConfKeys.im_paideia_dao_tokenid,
              DAOConfigValueSerializer(
                ErgoId.create(protoDAOProxyInputBox.daoGovernanceTokenId).getBytes
              )
            ),
            (
              ConfKeys.im_paideia_dao_key,
              DAOConfigValueSerializer[Array[Byte]](protoDAOProxyInput.getId().getBytes)
            ),
            (
              ConfKeys.im_paideia_dao_governance_type,
              DAOConfigValueSerializer[Byte](
                protoDAOProxyInputBox.governanceType.id.toByte
              )
            ),
            (
              ConfKeys.im_paideia_dao_quorum,
              DAOConfigValueSerializer(protoDAOProxyInputBox.quorum)
            ),
            (
              ConfKeys.im_paideia_dao_threshold,
              DAOConfigValueSerializer(protoDAOProxyInputBox.threshold)
            ),
            (
              ConfKeys.im_paideia_staking_emission_amount,
              DAOConfigValueSerializer[Long](protoDAOProxyInputBox.stakingEmissionAmount)
            ),
            (
              ConfKeys.im_paideia_staking_emission_delay,
              DAOConfigValueSerializer(protoDAOProxyInputBox.stakingEmissionDelay)
            ),
            (
              ConfKeys.im_paideia_staking_cyclelength,
              DAOConfigValueSerializer[Long](protoDAOProxyInputBox.stakingCycleLength)
            ),
            (
              ConfKeys.im_paideia_staking_profit_share_pct,
              DAOConfigValueSerializer[Byte](protoDAOProxyInputBox.stakingProfitSharePct)
            ),
            (
              ConfKeys.im_paideia_staking_weight_pureparticipation,
              DAOConfigValueSerializer[Byte](
                protoDAOProxyInputBox.pureParticipationWeight
              )
            ),
            (
              ConfKeys.im_paideia_staking_weight_participation,
              DAOConfigValueSerializer[Byte](protoDAOProxyInputBox.participationWeight)
            ),
            (
              ConfKeys.im_paideia_dao_url,
              DAOConfigValueSerializer(protoDAOProxyInputBox.url)
            ),
            (
              ConfKeys.im_paideia_dao_description,
              DAOConfigValueSerializer(protoDAOProxyInputBox.description)
            ),
            (
              ConfKeys.im_paideia_dao_logo,
              DAOConfigValueSerializer(protoDAOProxyInputBox.logo)
            ),
            (
              ConfKeys.im_paideia_dao_min_proposal_time,
              DAOConfigValueSerializer(protoDAOProxyInputBox.minProposalTime)
            ),
            (
              ConfKeys.im_paideia_dao_banner,
              DAOConfigValueSerializer(protoDAOProxyInputBox.banner)
            ),
            (
              ConfKeys.im_paideia_dao_banner_enabled,
              DAOConfigValueSerializer(protoDAOProxyInputBox.bannerEnabled)
            ),
            (
              ConfKeys.im_paideia_dao_footer,
              DAOConfigValueSerializer(protoDAOProxyInputBox.footer)
            ),
            (
              ConfKeys.im_paideia_dao_footer_enabled,
              DAOConfigValueSerializer(protoDAOProxyInputBox.footerEnabled)
            ),
            (
              ConfKeys.im_paideia_dao_theme,
              DAOConfigValueSerializer(protoDAOProxyInputBox.theme)
            )
          )(Left(newDAOConfig._config.digest))
        resultingDigest = Some(result._2)
        result._1
      }
    )
  )

  val protoDAOOutput = ProtoDAO(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
    .box(_ctx, newDAO, protoDAOProxyInputBox.stakePoolSize, resultingDigest)
  ctx           = _ctx
  fee           = 1000000
  changeAddress = _changeAddress
  inputs = List[InputBox](
    protoDAOProxyInput.withContextVars(contextVarsProtoDAOProxy: _*),
    paideiaOriginInput.withContextVars(contextVarPaideiaOrigin)
  )
  dataInputs = List[InputBox](paideiaConfigBox)
  outputs = List[OutBox](
    protoDAOOutput.outBox,
    paideiaOriginOutput.outBox,
    paideiaSplitProfitOutput.outBox,
    mintOutput.outBox
  )
  val bla = "meh"
}
