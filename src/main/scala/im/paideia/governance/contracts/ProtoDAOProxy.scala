package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.governance.boxes.ProtoDAOProxyBox
import org.ergoplatform.sdk.ErgoToken
import im.paideia.util.Env
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.governance.transactions.CreateProtoDAOTransaction
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import im.paideia.common.events.PaideiaEvent
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Address
import java.util.HashMap
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAOConfigKey
import org.ergoplatform.sdk.ErgoId
import java.nio.charset.StandardCharsets
import im.paideia.util.ConfKeys
import im.paideia.governance.GovernanceType
import work.lithos.plasma.collections.PlasmaMap
import sigmastate.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.transactions.RefundTransaction
import special.collection.Coll
import im.paideia.Paideia
import im.paideia.DAO
import im.paideia.common.events.UpdateConfigEvent
import scorex.crypto.authds.ADDigest
import special.sigma.AvlTree
import im.paideia.DAOConfigValue
import work.lithos.plasma.ByteConversion
import im.paideia.DAOConfigValueDeserializer

class ProtoDAOProxy(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    paideiaDaoConfig: DAOConfig,
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
  ): ProtoDAOProxyBox = {
    ProtoDAOProxyBox(
      ctx,
      paideiaDaoConfig,
      this,
      daoName,
      daoGovernanceTokenId,
      stakePoolSize,
      governanceType,
      quorum,
      threshold,
      stakingEmissionAmount,
      stakingEmissionDelay,
      stakingCycleLength,
      stakingProfitSharePct,
      userAddress,
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

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        PaideiaEventResponse.merge(
          getUtxoSet.toList.map { b =>
            PaideiaEventResponse(
              1,
              List(
                if (boxes(b).getCreationHeight() < cte.height - 30) {
                  RefundTransaction(
                    cte.ctx,
                    boxes(b),
                    Address.fromPropositionBytes(
                      NetworkType.MAINNET,
                      boxes(b)
                        .getRegisters()
                        .get(2)
                        .getValue()
                        .asInstanceOf[Coll[Byte]]
                        .toArray
                    )
                  )
                } else {
                  CreateProtoDAOTransaction(
                    cte.ctx,
                    boxes(b),
                    Address.create(Env.operatorAddress)
                  )
                }
              )
            )

          }.toList
        )
      }
      case te: TransactionEvent => {

        if (
          te.tx.getInputs().size() > 0 && getUtxoSet.contains(
            te.tx.getInputs().get(0).getBoxId()
          ) && te.tx
            .getInputs()
            .get(0)
            .getSpendingProof()
            .getExtension()
            .containsKey("2")
        ) {
          val protoDAOProxyInput = te.tx.getInputs().get(0)

          val newDaoKey             = protoDAOProxyInput.getBoxId()
          val protoDAOProxyInputBox = boxes(newDaoKey)
          val protoDAOProxyBox =
            ProtoDAOProxyBox.fromInputBox(te.ctx, protoDAOProxyInputBox)
          if (!Paideia._daoMap.contains(newDaoKey)) {
            val newDAOConfig = DAOConfig(newDaoKey)
            Paideia.addDAO(new DAO(newDaoKey, newDAOConfig))
          }

          Paideia
            .getConfig(newDaoKey)
            .handleUpdateEvent(
              UpdateConfigEvent(
                te.ctx,
                newDaoKey,
                if (te.mempool)
                  Left(
                    Paideia.getConfig(newDaoKey)._config.digest
                  )
                else
                  Right(te.height),
                Array[DAOConfigKey](),
                Array[(DAOConfigKey, Array[Byte])](),
                protoDAOProxyBox.insertOperations(protoDAOProxyInputBox.getId().getBytes)
              )
            )
          PaideiaEventResponse(1)
        } else {
          PaideiaEventResponse(0)
        }

      }
      case _ => PaideiaEventResponse(0)
    }
    PaideiaEventResponse.merge(List(super.handleEvent(event), response))
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_PROTODAO",
      ConfKeys.im_paideia_contracts_protodao.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_MINT",
      ConfKeys.im_paideia_contracts_mint.ergoValue.getValue()
    )
    cons.put("_PAIDEIA_DAO_KEY", ErgoId.create(Env.paideiaDaoKey).getBytes)
    cons.put(
      "_EMPTY_CONFIG_DIGEST",
      ErgoValue
        .of(
          new PlasmaMap[DAOConfigKey, Array[Byte]](
            AvlTreeFlags.AllOperationsAllowed,
            PlasmaParameters.default
          ).digest.array
        )
        .getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_NAME",
      ConfKeys.im_paideia_dao_name.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_GOVERNANCE_TOKEN_ID",
      ConfKeys.im_paideia_dao_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_KEY",
      ConfKeys.im_paideia_dao_key.ergoValue.getValue()
    )
    cons.put(
      "_DAO_KEY",
      ErgoValue.of(" DAO Key".getBytes(StandardCharsets.UTF_8)).getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_GOVERNANCE_TYPE",
      ConfKeys.im_paideia_dao_governance_type.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_QUORUM",
      ConfKeys.im_paideia_dao_quorum.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_THRESHOLD",
      ConfKeys.im_paideia_dao_threshold.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_EMISSION_AMOUNT",
      ConfKeys.im_paideia_staking_emission_amount.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_EMISSION_DELAY",
      ConfKeys.im_paideia_staking_emission_delay.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_CYCLE_LENGTH",
      ConfKeys.im_paideia_staking_cyclelength.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_PROFITSHARE_PCT",
      ConfKeys.im_paideia_staking_profit_share_pct.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_PUREPARTICIPATION_WEIGHT",
      ConfKeys.im_paideia_staking_weight_pureparticipation.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_PARTICIPATION_WEIGHT",
      ConfKeys.im_paideia_staking_weight_participation.ergoValue.getValue()
    )
    if (contractSignature.version.startsWith("1.1.")) {
      cons.put(
        "_IM_PAIDEIA_DAO_URL",
        ConfKeys.im_paideia_dao_url.ergoValue.getValue()
      )
      cons.put(
        "_IM_PAIDEIA_DAO_DESCRIPTION",
        ConfKeys.im_paideia_dao_description.ergoValue.getValue()
      )
      cons.put(
        "_IM_PAIDEIA_DAO_LOGO",
        ConfKeys.im_paideia_dao_logo.ergoValue.getValue()
      )
      cons.put(
        "_IM_PAIDEIA_DAO_MIN_PROPOSAL_TIME",
        ConfKeys.im_paideia_dao_min_proposal_time.ergoValue.getValue()
      )
      cons.put(
        "_IM_PAIDEIA_DAO_BANNER",
        ConfKeys.im_paideia_dao_banner.ergoValue.getValue()
      )
      cons.put(
        "_IM_PAIDEIA_DAO_BANNER_ENABLED",
        ConfKeys.im_paideia_dao_banner_enabled.ergoValue.getValue()
      )
      cons.put(
        "_IM_PAIDEIA_DAO_FOOTER",
        ConfKeys.im_paideia_dao_footer.ergoValue.getValue()
      )
      cons.put(
        "_IM_PAIDEIA_DAO_FOOTER_ENABLED",
        ConfKeys.im_paideia_dao_footer_enabled.ergoValue.getValue()
      )
      cons.put(
        "_IM_PAIDEIA_DAO_THEME",
        ConfKeys.im_paideia_dao_theme.ergoValue.getValue()
      )
    }
    if (contractSignature.version.equals("1.1.2")) {
      cons.put(
        "_IM_PAIDEIA_STAKING_PROFIT_TOKENS",
        ConfKeys.im_paideia_staking_profit_tokenids.ergoValue.getValue()
      )
      cons.put(
        "_IM_PAIDEIA_STAKING_PROFIT_THRESHOLD",
        ConfKeys.im_paideia_staking_profit_thresholds.ergoValue.getValue()
      )
    }
    cons
  }
}

object ProtoDAOProxy extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): ProtoDAOProxy =
    getContractInstance[ProtoDAOProxy](
      contractSignature,
      new ProtoDAOProxy(contractSignature)
    )
}
