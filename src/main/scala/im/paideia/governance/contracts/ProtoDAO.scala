package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.governance.boxes.ProtoDAOBox
import org.ergoplatform.sdk.ErgoToken
import im.paideia.util.Env
import java.util.HashMap
import im.paideia.DAOConfigKey
import org.ergoplatform.sdk.ErgoId
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
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.events.UpdateConfigEvent
import im.paideia.DAOConfigValueSerializer
import im.paideia.common.transactions.PaideiaTransaction
import im.paideia.staking.TotalStakingState
import im.paideia.staking.contracts.StakeState
import im.paideia.staking.contracts.ChangeStake
import im.paideia.staking.contracts.Stake
import im.paideia.staking.contracts.Unstake
import im.paideia.staking.contracts.StakeCompound
import im.paideia.staking.contracts.StakeSnapshot
import im.paideia.staking.contracts.StakeVote
import im.paideia.staking.contracts.StakeProfitShare

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
      case cte: CreateTransactionsEvent => {
        PaideiaEventResponse.merge(
          getUtxoSet.toList.map { b =>
            {
              try {

                val iBox = boxes(b)
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
                if (
                  config._config.ergoAVLTree().digest == iBox
                    .getRegisters()
                    .get(0)
                    .getValue()
                    .asInstanceOf[AvlTree]
                    .digest
                ) {
                  val tokensToMint = ProtoDAO.tokensToMint.filter((s: DAOConfigKey) =>
                    config._config.lookUp(s).response(0).tryOp.get == None
                  )
                  if (iBox.getValue() > 6000000L) {
                    if (tokensToMint.size > 0) {
                      PaideiaEventResponse(
                        2,
                        List(
                          MintTransaction(
                            cte.ctx,
                            iBox,
                            dao,
                            tokensToMint(0),
                            Address.create(Env.operatorAddress)
                          )
                        )
                      )
                    } else {
                      try {
                        val newTx = CreateDAOTransaction(
                          cte.ctx,
                          iBox,
                          dao,
                          Address.create(Env.operatorAddress)
                        )
                        PaideiaEventResponse(2, List(newTx))
                      } catch {
                        case _: Exception => PaideiaEventResponse(0)
                      }
                    }
                  } else {
                    PaideiaEventResponse(0)
                  }
                } else {
                  PaideiaEventResponse(0)
                }

              } catch {
                case e: Exception =>
                  PaideiaEventResponse(-1, List[PaideiaTransaction](), List(e))
              }
            }
          }.toList
        )
      }
      case te: TransactionEvent => {

        if (
          te.tx.getInputs().size() > 0 &&
          te.tx.getInputs().size() < 4 && getUtxoSet.contains(
            te.tx.getInputs().get(0).getBoxId()
          )
        ) {
          val protoDAOInput = te.tx.getInputs().get(0)
          val protoDAOBox =
            ProtoDAOBox.fromInputBox(te.ctx, boxes(protoDAOInput.getBoxId()))

          val context = protoDAOInput
            .getSpendingProof()
            .getExtension()
            .asScala
            .map((kv: (String, String)) => (kv._1.toByte, ErgoValue.fromHex(kv._2)))
            .toMap[Byte, ErgoValue[_]]

          Paideia
            .getConfig(protoDAOBox.dao.key)
            .handleUpdateEvent(
              UpdateConfigEvent(
                te.ctx,
                protoDAOBox.dao.key,
                if (te.mempool)
                  Left(
                    protoDAOBox.digestOpt.get
                  )
                else
                  Right(te.height),
                Array[DAOConfigKey](),
                Array[(DAOConfigKey, Array[Byte])](),
                Array(
                  (
                    DAOConfigKey.convertsDAOConfigKey.convertFromBytes(
                      context(3.toByte)
                        .getValue()
                        .asInstanceOf[Coll[Byte]]
                        .toArray
                    ),
                    DAOConfigValueSerializer[Array[Byte]](
                      ErgoId.create(protoDAOInput.getBoxId()).getBytes
                    )
                  )
                )
              )
            )
          PaideiaEventResponse(1)
        } else {
          if (
            te.tx.getInputs().size() > 4 && getUtxoSet.contains(
              te.tx.getInputs().get(0).getBoxId()
            )
          ) {
            val protoDAOInput = te.tx.getInputs().get(0)
            val protoDAOBox =
              ProtoDAOBox.fromInputBox(te.ctx, boxes(protoDAOInput.getBoxId()))
            Paideia
              .getConfig(protoDAOBox.dao.key)
              .handleUpdateEvent(
                UpdateConfigEvent(
                  te.ctx,
                  protoDAOBox.dao.key,
                  if (te.mempool)
                    Left(
                      protoDAOBox.digestOpt.get
                    )
                  else
                    Right(te.height),
                  Array[DAOConfigKey](),
                  Array[(DAOConfigKey, Array[Byte])](),
                  CreateDAO(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
                    .getInsertOperations(protoDAOBox.dao)
                )
              )
            val castVoteContract = CastVote(
              PaideiaContractSignature(daoKey = protoDAOBox.dao.key)
            )
            // If the staking state does not exist we need to initiate it and any contractinstances in the output
            if (!TotalStakingState._stakingStates.contains(protoDAOBox.dao.key)) {
              val stakeStateBox = te.tx.getOutputs().get(2)
              val _state = TotalStakingState(
                protoDAOBox.dao.key,
                ErgoValue
                  .fromHex(stakeStateBox.getAdditionalRegisters().get("R5"))
                  .getValue()
                  .asInstanceOf[Coll[Long]](0)
              )
              DAOOrigin(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(0)),
                  te.mempool,
                  te.rollback
                )
              Config(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(1)),
                  te.mempool,
                  te.rollback
                )
              StakeState(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(2)),
                  te.mempool,
                  te.rollback
                )
              ChangeStake(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(3)),
                  te.mempool,
                  te.rollback
                )
              Stake(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(4)),
                  te.mempool,
                  te.rollback
                )
              Unstake(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(5)),
                  te.mempool,
                  te.rollback
                )
              StakeCompound(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(6)),
                  te.mempool,
                  te.rollback
                )
              StakeSnapshot(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(7)),
                  te.mempool,
                  te.rollback
                )
              StakeVote(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(8)),
                  te.mempool,
                  te.rollback
                )
              StakeProfitShare(PaideiaContractSignature(daoKey = protoDAOBox.dao.key))
                .newBox(
                  new InputBoxImpl(te.tx.getOutputs().get(9)),
                  te.mempool,
                  te.rollback
                )
            }
            PaideiaEventResponse(1)
          } else {
            PaideiaEventResponse(0)
          }
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
    cons.put("_PAIDEIA_DAO_KEY", ErgoId.create(Env.paideiaDaoKey).getBytes)
    cons.put("_IM_PAIDEIA_DAO_NAME", ConfKeys.im_paideia_dao_name.ergoValue.getValue())
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
      "_IM_PAIDEIA_CONTRACTS_CREATE_DAO",
      ConfKeys.im_paideia_contracts_createdao.ergoValue.getValue()
    )
    cons
  }

  override def getConfigContext(configDigest: Option[ADDigest]) = Paideia
    .getConfig(contractSignature.daoKey)
    .getProof(
      ConfKeys.im_paideia_contracts_createdao
    )(configDigest)

}

object ProtoDAO extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): ProtoDAO =
    getContractInstance[ProtoDAO](contractSignature, new ProtoDAO(contractSignature))

  val tokensToMint = List(
    ConfKeys.im_paideia_dao_proposal_tokenid,
    ConfKeys.im_paideia_dao_action_tokenid,
    ConfKeys.im_paideia_staking_state_tokenid
  )
}
