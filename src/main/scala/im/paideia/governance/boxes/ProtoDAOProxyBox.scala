package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.boxes.ConfigBox
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import im.paideia.DAOConfigValue
import im.paideia.DAOConfigValueSerializer
import org.ergoplatform.appkit.ErgoId
import special.collection.Coll
import org.ergoplatform.appkit.ErgoType
import special.collection.CollOverArray
import im.paideia.governance.contracts.ProtoDAOProxy
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfigValueDeserializer
import im.paideia.Paideia
import scorex.crypto.hash.Blake2b256
import im.paideia.util.ConfKeys
import sigmastate.eval.Colls
import im.paideia.governance.GovernanceType

case class ProtoDAOProxyBox(
  _ctx: BlockchainContextImpl,
  paideiaDaoConfig: DAOConfig,
  useContract: ProtoDAOProxy,
  daoName: String,
  daoGovernanceTokenId: String,
  stakePoolSize: Long,
  governanceType: GovernanceType.Value,
  quorum: Byte,
  threshold: Byte,
  stakingEmissionAmount: Long,
  stakingEmissionDelay: Byte,
  stakingCycleLength: Long,
  stakingProfitSharePct: Byte
) extends PaideiaBox {

  ctx = _ctx
  value = ProtoDAO.tokensToMint.size * 2000000L + paideiaDaoConfig[Long](
    ConfKeys.im_paideia_fees_createdao_erg
  ) + 10000000L
  contract = useContract.contract

  override def registers: List[ErgoValue[_]] = {
    List(
      ErgoValueBuilder.buildFor(
        Colls.fromArray(
          Array(
            Colls.fromArray(DAOConfigValueSerializer(daoName)),
            Colls.fromArray(
              DAOConfigValueSerializer(
                ErgoId.create(daoGovernanceTokenId).getBytes().asInstanceOf[Array[Byte]]
              )
            ),
            Colls.fromArray(DAOConfigValueSerializer(governanceType.id.toByte)),
            Colls.fromArray(DAOConfigValueSerializer(quorum)),
            Colls.fromArray(DAOConfigValueSerializer(threshold)),
            Colls.fromArray(DAOConfigValueSerializer(stakingEmissionAmount)),
            Colls.fromArray(DAOConfigValueSerializer(stakingEmissionDelay)),
            Colls.fromArray(DAOConfigValueSerializer(stakingCycleLength)),
            Colls.fromArray(DAOConfigValueSerializer(stakingProfitSharePct))
          )
        )
      ),
      ErgoValueBuilder.buildFor(
        Colls.fromArray(
          Array(
            stakePoolSize
          )
        )
      )
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
}

object ProtoDAOProxyBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ProtoDAOProxyBox = {

    val byteRegister =
      inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Coll[Byte]]].map(_.toArray)
    val longRegister  = inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]]
    val stakePoolSize = longRegister(0)
    val daoName =
      DAOConfigValueDeserializer.deserialize(byteRegister(0).toArray).asInstanceOf[String]
    val daoTokenId = new ErgoId(
      DAOConfigValueDeserializer
        .deserialize(byteRegister(1).toArray)
        .asInstanceOf[Array[Any]]
        .map(_.asInstanceOf[Byte])
    )
    val daoGovernanceType: Byte     = DAOConfigValueDeserializer(byteRegister(2))
    val quorum: Byte                = DAOConfigValueDeserializer(byteRegister(3))
    val threshold: Byte             = DAOConfigValueDeserializer(byteRegister(4))
    val stakingEmissionAmount: Long = DAOConfigValueDeserializer(byteRegister(5))
    val stakingEmissionDelay: Byte  = DAOConfigValueDeserializer(byteRegister(6))
    val stakingCycleLength: Long    = DAOConfigValueDeserializer(byteRegister(7))
    val stakingProfitSharePct: Byte = DAOConfigValueDeserializer(byteRegister(8))

    ProtoDAOProxyBox(
      ctx,
      Paideia.getConfig(Env.paideiaDaoKey),
      ProtoDAOProxy
        .contractInstances(Blake2b256(inp.getErgoTree.bytes).array.toList)
        .asInstanceOf[ProtoDAOProxy],
      daoName,
      daoTokenId.toString(),
      inp.getTokens().toArray.foldLeft[Long](0L) { (z: Long, token) =>
        z + (if (token.asInstanceOf[ErgoToken].getId() == daoTokenId)
               token.asInstanceOf[ErgoToken].getValue() - 1L
             else 0L)
      },
      GovernanceType(daoGovernanceType.toInt),
      quorum,
      threshold,
      stakingEmissionAmount,
      stakingEmissionDelay,
      stakingCycleLength,
      stakingProfitSharePct
    )
  }
}
