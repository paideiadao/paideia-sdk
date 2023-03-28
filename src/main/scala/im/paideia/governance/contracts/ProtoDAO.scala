package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.governance.boxes.ProtoDAOBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import java.util.HashMap
import im.paideia.DAOConfigKey
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ErgoValue
import java.nio.charset.StandardCharsets
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import scala.collection.JavaConverters._
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.Paideia
import org.ergoplatform.appkit.impl.InputBoxImpl
import special.sigma.AvlTree
import im.paideia.governance.transactions.MintTransaction
import org.ergoplatform.appkit.Address
import im.paideia.util.ConfKeys
import im.paideia.governance.transactions.CreateDAOTransaction
import im.paideia.DAO
import special.collection.Coll
import scorex.crypto.authds.ADDigest

class ProtoDAO(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    dao: DAO,
    stakePoolSize: Long,
    digestOpt: Option[ADDigest] = None,
    value: Long                 = 1000000L
  ): ProtoDAOBox = {
    ProtoDAOBox(ctx, dao, stakePoolSize, this, value, digestOpt)
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case te: TransactionEvent => {
        PaideiaEventResponse.merge(
          te.tx
            .getOutputs()
            .asScala
            .map { (eto: ErgoTransactionOutput) =>
              {
                if (eto.getErgoTree() == ergoTree.bytesHex) {
                  val iBox = new InputBoxImpl(eto)
                  val dao = Paideia.getDAO(
                    new ErgoId(
                      iBox
                        .getRegisters()
                        .get(1)
                        .getValue()
                        .asInstanceOf[Coll[Byte]]
                        .toArray
                    ).toString()
                  )
                  val config = dao.config
                  if (config._config.ergoAVLTree().digest == iBox
                        .getRegisters()
                        .get(0)
                        .getValue()
                        .asInstanceOf[AvlTree]
                        .digest) {
                    val nextTokenToMint = ProtoDAO.tokensToMint.find((s: DAOConfigKey) =>
                      config._config.lookUp(s).response(0).tryOp.get == None
                    )
                    nextTokenToMint match {
                      case Some(value) =>
                        PaideiaEventResponse(
                          2,
                          List(
                            MintTransaction(
                              te._ctx,
                              iBox,
                              dao,
                              value,
                              Address.create(Env.operatorAddress).getErgoAddress()
                            )
                          )
                        )
                      case None => {
                        val newTx = CreateDAOTransaction(
                          te._ctx,
                          iBox,
                          dao,
                          Address.create(Env.operatorAddress).getErgoAddress()
                        )
                        PaideiaEventResponse(2, List(newTx))
                      }
                    }
                  } else {
                    PaideiaEventResponse(0)
                  }
                } else {
                  PaideiaEventResponse(0)
                }
              }
            }
            .toList
        )
      }
      case _ => PaideiaEventResponse(0)
    }
    val superResponse = super.handleEvent(event)
    response
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
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_DAO",
      ConfKeys.im_paideia_contracts_dao.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_TREASURY",
      ConfKeys.im_paideia_contracts_treasury.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_CONFIG",
      ConfKeys.im_paideia_contracts_config.ergoValue.getValue()
    )
    cons.put("_IM_PAIDEIA_DAO_KEY", ConfKeys.im_paideia_dao_key.ergoValue.getValue())
    cons.put("_PAIDEIA_DAO_KEY", ErgoId.create(Env.paideiaDaoKey).getBytes())
    cons.put("_IM_PAIDEIA_DAO_NAME", ConfKeys.im_paideia_dao_name.ergoValue.getValue())
    cons.put(
      "_IM_PAIDEIA_DAO_VOTE_TOKENID",
      ConfKeys.im_paideia_dao_vote_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_PROPOSAL_TOKENID",
      ConfKeys.im_paideia_dao_proposal_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_ACTION_TOKENID",
      ConfKeys.im_paideia_dao_action_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_STATE_TOKENID",
      ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue()
    )
    cons.put("_VOTE", ErgoValue.of(" Vote".getBytes(StandardCharsets.UTF_8)).getValue())
    cons.put(
      "_PROPOSAL",
      ErgoValue.of(" Proposal".getBytes(StandardCharsets.UTF_8)).getValue()
    )
    cons.put(
      "_ACTION",
      ErgoValue.of(" Action".getBytes(StandardCharsets.UTF_8)).getValue()
    )
    cons.put(
      "_STAKE_STATE",
      ErgoValue.of(" Stake State".getBytes(StandardCharsets.UTF_8)).getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_TREASURY",
      ConfKeys.im_paideia_default_treasury.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_TREASURY_SIGNATURE",
      ConfKeys.im_paideia_default_treasury_signature.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_CONFIG",
      ConfKeys.im_paideia_default_config.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DEFAULT_CONFIG_SIGNATURE",
      ConfKeys.im_paideia_default_config_signature.ergoValue.getValue()
    )
    cons
  }
}

object ProtoDAO extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): ProtoDAO =
    getContractInstance[ProtoDAO](contractSignature, new ProtoDAO(contractSignature))

  val tokensToMint = List(
    ConfKeys.im_paideia_dao_vote_tokenid,
    ConfKeys.im_paideia_dao_proposal_tokenid,
    ConfKeys.im_paideia_dao_action_tokenid,
    ConfKeys.im_paideia_staking_state_tokenid
  )
}
