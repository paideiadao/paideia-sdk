package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoToken
import im.paideia.util.Env
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.boxes.ConfigBox
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import im.paideia.DAOConfigValueSerializer
import org.ergoplatform.sdk.ErgoId
import sigma.Coll
import org.ergoplatform.appkit.ErgoType
import im.paideia.governance.contracts.ProtoDAOProxy
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfigValueDeserializer
import im.paideia.Paideia
import scorex.crypto.hash.Blake2b256
import im.paideia.util.ConfKeys
import sigma.Colls
import im.paideia.governance.GovernanceType
import org.ergoplatform.appkit.Address
import im.paideia.DAOConfigKey

case class ProtoDAOProxyBox(
  _ctx: BlockchainContextImpl,
  paideiaDaoConfig: DAOConfig,
  useContract: ProtoDAOProxy,
  daoName: String,
  daoGovernanceTokenId: String,
  stakePoolSize: Long,
  governanceType: GovernanceType.Value,
  quorum: Long,
  threshold: Long,
  stakingEmissionAmount: Long,
  stakingEmissionDelay: Long,
  stakingCycleLength: Long,
  stakingProfitSharePct: Byte,
  userAddress: Address,
  pureParticipationWeight: Byte,
  participationWeight: Byte,
  url: String,
  description: String,
  logo: String,
  minProposalTime: Long,
  banner: String,
  bannerEnabled: Boolean,
  footer: String,
  footerEnabled: Boolean,
  theme: String
) extends PaideiaBox {

  ctx = _ctx
  value = ProtoDAO.tokensToMint.size * 2000000L + paideiaDaoConfig[Long](
    ConfKeys.im_paideia_fees_createdao_erg
  ) + 3007000000L
  contract = useContract.contract

  override def registers: List[ErgoValue[_]] = {
    val configValues = Array(
      Colls.fromArray(DAOConfigValueSerializer(daoName)),
      Colls.fromArray(
        DAOConfigValueSerializer(
          ErgoId.create(daoGovernanceTokenId).getBytes.asInstanceOf[Array[Byte]]
        )
      ),
      Colls.fromArray(DAOConfigValueSerializer(governanceType.id.toByte)),
      Colls.fromArray(DAOConfigValueSerializer(quorum)),
      Colls.fromArray(DAOConfigValueSerializer(threshold)),
      Colls.fromArray(DAOConfigValueSerializer(stakingEmissionAmount)),
      Colls.fromArray(DAOConfigValueSerializer(stakingEmissionDelay)),
      Colls.fromArray(DAOConfigValueSerializer(stakingCycleLength)),
      Colls.fromArray(DAOConfigValueSerializer(stakingProfitSharePct)),
      Colls.fromArray(DAOConfigValueSerializer(pureParticipationWeight)),
      Colls.fromArray(DAOConfigValueSerializer(participationWeight)),
      Colls.fromArray(DAOConfigValueSerializer(url)),
      Colls.fromArray(DAOConfigValueSerializer(description)),
      Colls.fromArray(DAOConfigValueSerializer(logo)),
      Colls.fromArray(DAOConfigValueSerializer(minProposalTime)),
      Colls.fromArray(DAOConfigValueSerializer(banner)),
      Colls.fromArray(DAOConfigValueSerializer(bannerEnabled)),
      Colls.fromArray(DAOConfigValueSerializer(footer)),
      Colls.fromArray(DAOConfigValueSerializer(footerEnabled)),
      Colls.fromArray(DAOConfigValueSerializer(theme))
    )
    List(
      ErgoValueBuilder.buildFor(
        Colls.fromArray(
          configValues
        )
      ),
      ErgoValueBuilder.buildFor(
        Colls.fromArray(
          Array(
            stakePoolSize
          )
        )
      ),
      ErgoValueBuilder.buildFor(Colls.fromArray(userAddress.toPropositionBytes()))
    )
  }

  override def tokens: List[ErgoToken] = {
    {
      if (paideiaDaoConfig[Long](ConfKeys.im_paideia_fees_createdao_paideia) > 0L)
        List(
          new ErgoToken(
            Env.paideiaTokenId,
            paideiaDaoConfig(ConfKeys.im_paideia_fees_createdao_paideia)
          )
        )
      else
        List[ErgoToken]()
    } ++
    List(new ErgoToken(daoGovernanceTokenId, stakePoolSize + 1L))
  }

  def insertOperations(newDaoKey: Array[Byte]): Array[(DAOConfigKey, Array[Byte])] = {
    Array(
      (
        ConfKeys.im_paideia_dao_name,
        DAOConfigValueSerializer(daoName)
      ),
      (
        ConfKeys.im_paideia_dao_tokenid,
        DAOConfigValueSerializer(
          ErgoId.create(daoGovernanceTokenId).getBytes
        )
      ),
      (
        ConfKeys.im_paideia_dao_key,
        DAOConfigValueSerializer[Array[Byte]](newDaoKey)
      ),
      (
        ConfKeys.im_paideia_dao_governance_type,
        DAOConfigValueSerializer[Byte](
          governanceType.id.toByte
        )
      ),
      (
        ConfKeys.im_paideia_dao_quorum,
        DAOConfigValueSerializer(quorum)
      ),
      (
        ConfKeys.im_paideia_dao_threshold,
        DAOConfigValueSerializer(threshold)
      ),
      (
        ConfKeys.im_paideia_staking_emission_amount,
        DAOConfigValueSerializer[Long](stakingEmissionAmount)
      ),
      (
        ConfKeys.im_paideia_staking_emission_delay,
        DAOConfigValueSerializer(stakingEmissionDelay)
      ),
      (
        ConfKeys.im_paideia_staking_cyclelength,
        DAOConfigValueSerializer[Long](stakingCycleLength)
      ),
      (
        ConfKeys.im_paideia_staking_profit_share_pct,
        DAOConfigValueSerializer[Byte](stakingProfitSharePct)
      ),
      (
        ConfKeys.im_paideia_staking_weight_pureparticipation,
        DAOConfigValueSerializer[Byte](
          pureParticipationWeight
        )
      ),
      (
        ConfKeys.im_paideia_staking_weight_participation,
        DAOConfigValueSerializer[Byte](participationWeight)
      ),
      (
        ConfKeys.im_paideia_dao_url,
        DAOConfigValueSerializer(url)
      ),
      (
        ConfKeys.im_paideia_dao_description,
        DAOConfigValueSerializer(description)
      ),
      (
        ConfKeys.im_paideia_dao_logo,
        DAOConfigValueSerializer(logo)
      ),
      (
        ConfKeys.im_paideia_dao_min_proposal_time,
        DAOConfigValueSerializer(minProposalTime)
      ),
      (
        ConfKeys.im_paideia_dao_banner,
        DAOConfigValueSerializer(banner)
      ),
      (
        ConfKeys.im_paideia_dao_banner_enabled,
        DAOConfigValueSerializer(bannerEnabled)
      ),
      (
        ConfKeys.im_paideia_dao_footer,
        DAOConfigValueSerializer(footer)
      ),
      (
        ConfKeys.im_paideia_dao_footer_enabled,
        DAOConfigValueSerializer(footerEnabled)
      ),
      (
        ConfKeys.im_paideia_dao_theme,
        DAOConfigValueSerializer(theme)
      )
    )
  }
}

object ProtoDAOProxyBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ProtoDAOProxyBox = {

    val byteRegister =
      inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Coll[Byte]]].map(_.toArray)
    val longRegister  = inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]]
    val stakePoolSize = longRegister(0)
    val daoName: String =
      DAOConfigValueDeserializer.deserialize(byteRegister(0).toArray).asInstanceOf[String]
    val daoTokenId = new ErgoId(
      DAOConfigValueDeserializer
        .deserialize(byteRegister(1).toArray)
        .asInstanceOf[Array[Any]]
        .map(_.asInstanceOf[Byte])
    )
    val boxContract = ProtoDAOProxy
      .contractInstances(Blake2b256(inp.getErgoTree.bytes).array.toList)
      .asInstanceOf[ProtoDAOProxy]
    val daoGovernanceType: Byte       = DAOConfigValueDeserializer(byteRegister(2))
    val quorum: Long                  = DAOConfigValueDeserializer(byteRegister(3))
    val threshold: Long               = DAOConfigValueDeserializer(byteRegister(4))
    val stakingEmissionAmount: Long   = DAOConfigValueDeserializer(byteRegister(5))
    val stakingEmissionDelay: Long    = DAOConfigValueDeserializer(byteRegister(6))
    val stakingCycleLength: Long      = DAOConfigValueDeserializer(byteRegister(7))
    val stakingProfitSharePct: Byte   = DAOConfigValueDeserializer(byteRegister(8))
    val pureParticipationWeight: Byte = DAOConfigValueDeserializer(byteRegister(9))
    val participationWeight: Byte     = DAOConfigValueDeserializer(byteRegister(10))
    val url: String                   = DAOConfigValueDeserializer(byteRegister(11))
    val description: String           = DAOConfigValueDeserializer(byteRegister(12))
    val logo: String                  = DAOConfigValueDeserializer(byteRegister(13))
    val minProposalTime: Long         = DAOConfigValueDeserializer(byteRegister(14))
    val banner: String                = DAOConfigValueDeserializer(byteRegister(15))
    val bannerEnabled: Boolean        = DAOConfigValueDeserializer(byteRegister(16))
    val footer: String                = DAOConfigValueDeserializer(byteRegister(17))
    val footerEnabled: Boolean        = DAOConfigValueDeserializer(byteRegister(18))
    val theme: String                 = DAOConfigValueDeserializer(byteRegister(19))

    ProtoDAOProxyBox(
      ctx,
      Paideia.getConfig(Env.paideiaDaoKey),
      boxContract,
      daoName,
      daoTokenId.toString(),
      inp.getTokens().toArray.foldLeft[Long](0L) { (z: Long, token) =>
        z + (if (token.asInstanceOf[ErgoToken].getId == daoTokenId)
               token.asInstanceOf[ErgoToken].getValue - 1L
             else 0L)
      },
      GovernanceType(daoGovernanceType.toInt),
      quorum,
      threshold,
      stakingEmissionAmount,
      stakingEmissionDelay,
      stakingCycleLength,
      stakingProfitSharePct,
      Address.fromPropositionBytes(
        ctx.getNetworkType(),
        inp.getRegisters().get(2).getValue().asInstanceOf[Coll[Byte]].toArray
      ),
      pureParticipationWeight,
      participationWeight,
      url,
      description,
      logo,
      minProposalTime,
      banner,
      bannerEnabled,
      footer,
      footerEnabled,
      theme
    )
  }
}
