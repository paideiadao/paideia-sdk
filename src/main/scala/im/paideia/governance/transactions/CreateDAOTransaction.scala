package im.paideia.governance.transactions

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfig
import org.ergoplatform.ErgoAddress
import im.paideia.Paideia
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.util.Env
import im.paideia.common.filtering.CompareField
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Config
import im.paideia.DAO
import org.ergoplatform.appkit.ContextVar
import im.paideia.DAOConfigKey
import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.OutBox
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoId
import javax.naming.Context
import im.paideia.staking.contracts.StakeState
import im.paideia.staking.TotalStakingState
import im.paideia.governance.boxes.ProtoDAOBox
import im.paideia.common.contracts.Treasury
import im.paideia.DAOConfigValueSerializer
import sigmastate.eval.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scorex.crypto.authds.ADDigest
import special.sigma.AvlTree
import org.ergoplatform.sdk.ErgoToken
import im.paideia.governance.contracts.ActionSendFundsBasic
import im.paideia.governance.contracts.ActionUpdateConfig
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.staking.contracts.ChangeStake
import im.paideia.staking.contracts.Stake
import im.paideia.staking.contracts.StakeCompound
import im.paideia.staking.contracts.StakeProfitShare
import im.paideia.staking.contracts.StakeSnapshot
import im.paideia.staking.contracts.StakeVote
import im.paideia.staking.contracts.Unstake
import im.paideia.governance.contracts.CreateDAO
import org.ergoplatform.appkit.Address

case class CreateDAOTransaction(
  _ctx: BlockchainContextImpl,
  protoDAOInput: InputBox,
  dao: DAO,
  _changeAddress: Address
) extends PaideiaTransaction {
  ctx = _ctx
  val protoDAOInputBox = ProtoDAOBox.fromInputBox(_ctx, protoDAOInput)

  val paideiaConfigBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      Env.paideiaDaoKey,
      CompareField.ASSET,
      0
    )
  )(0)

  val actionMintBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid))
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val proposalMintBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid))
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val daoKeyMintBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_key)).toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val stakeStateMintBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid))
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)

  val daoOriginOutput = DAOOrigin(
    PaideiaContractSignature(networkType = _ctx.getNetworkType(), daoKey = dao.key)
  ).box(
    _ctx,
    dao,
    Long.MaxValue,
    Long.MaxValue
  )

  val configContract = Config(
    PaideiaContractSignature(networkType = _ctx.getNetworkType(), daoKey = dao.key)
  )
  val configContractSignature = configContract.contractSignature

  val emissionTime = _ctx.createPreHeader().build().getTimestamp() + dao.config[Long](
    ConfKeys.im_paideia_staking_cyclelength
  ) - 600000L

  val state = TotalStakingState(
    dao.key,
    emissionTime
  )

  val stakeStateOutput = StakeState(PaideiaContractSignature(daoKey = dao.key))
    .emptyBox(_ctx, dao, protoDAOInputBox.stakePool)

  val treasuryContract          = Treasury(PaideiaContractSignature(daoKey = dao.key))
  val treasuryContractSignature = treasuryContract.contractSignature
  val actionSendFundsContract = ActionSendFundsBasic(
    PaideiaContractSignature(daoKey = dao.key)
  )
  val actionSendFundsContractSignature = actionSendFundsContract.contractSignature
  val actionUpdateConfigContract = ActionUpdateConfig(
    PaideiaContractSignature(daoKey = dao.key)
  )
  val actionUpdateConfigContractSignature = actionUpdateConfigContract.contractSignature
  val proposalBasicContract = ProposalBasic(
    PaideiaContractSignature(daoKey = dao.key)
  )
  val proposalBasicContractSignature = proposalBasicContract.contractSignature
  val stakingChangeContract = ChangeStake(PaideiaContractSignature(daoKey = dao.key))
  val stakingChangeContractSignature = stakingChangeContract.contractSignature
  val stakingStakeContract           = Stake(PaideiaContractSignature(daoKey = dao.key))
  val stakingStakeContractSignature  = stakingStakeContract.contractSignature
  val stakingCompoundContract = StakeCompound(PaideiaContractSignature(daoKey = dao.key))
  val stakingCompoundContractSignature = stakingCompoundContract.contractSignature
  val stakingProfitShareContract = StakeProfitShare(
    PaideiaContractSignature(daoKey = dao.key)
  )
  val stakingProfitShareContractSignature = stakingProfitShareContract.contractSignature
  val stakingSnapshotContract = StakeSnapshot(PaideiaContractSignature(daoKey = dao.key))
  val stakingSnapshotContractSignature = stakingSnapshotContract.contractSignature
  val stakingVoteContract          = StakeVote(PaideiaContractSignature(daoKey = dao.key))
  val stakingVoteContractSignature = stakingVoteContract.contractSignature
  val stakingUnstakeContract       = Unstake(PaideiaContractSignature(daoKey = dao.key))
  val stakingUnstakeContractSignature = stakingUnstakeContract.contractSignature

  val createDaoContract = CreateDAO(
    paideiaConfig[PaideiaContractSignature](
      ConfKeys.im_paideia_contracts_createdao
    )
      .withDaoKey(Env.paideiaDaoKey)
  )
  val createDaoInput =
    createDaoContract.boxes(createDaoContract.getUtxoSet.toArray.apply(0))

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

  val mintPaideiaConfigProof = ContextVar.of(
    0.toByte,
    paideiaConfig
      .getProof(
        ConfKeys.im_paideia_contracts_protodao,
        ConfKeys.im_paideia_contracts_dao
      )(Some(paideiaConfigDigest))
  )

  val proposalMintContext = List(
    mintPaideiaConfigProof,
    ContextVar
      .of(
        1.toByte,
        dao.config.getProof(ConfKeys.im_paideia_dao_proposal_tokenid)(Some(configDigest))
      ),
    ContextVar.of(2.toByte, ConfKeys.im_paideia_dao_proposal_tokenid.ergoValue)
  )

  val actionMintContext = List(
    mintPaideiaConfigProof,
    ContextVar.of(
      1.toByte,
      dao.config.getProof(ConfKeys.im_paideia_dao_action_tokenid)(Some(configDigest))
    ),
    ContextVar.of(2.toByte, ConfKeys.im_paideia_dao_action_tokenid.ergoValue)
  )

  val daoKeyMintContext = List(
    mintPaideiaConfigProof,
    ContextVar
      .of(1.toByte, dao.config.getProof(ConfKeys.im_paideia_dao_key)(Some(configDigest))),
    ContextVar.of(2.toByte, ConfKeys.im_paideia_dao_key.ergoValue)
  )

  val stakeStateMintContext = List(
    mintPaideiaConfigProof,
    ContextVar
      .of(
        1.toByte,
        dao.config.getProof(ConfKeys.im_paideia_staking_state_tokenid)(Some(configDigest))
      ),
    ContextVar.of(2.toByte, ConfKeys.im_paideia_staking_state_tokenid.ergoValue)
  )

  var resultingDigest: Option[ADDigest] = None

  val contextVarsCreateDAO = List(
    ContextVar.of(
      0.toByte,
      createDaoContract.getConfigContext(Some(paideiaConfigDigest))
    ),
    ContextVar.of(
      1.toByte,
      createDaoContract.getDAOConfigContext(dao.config, Some(configDigest))
    ),
    ContextVar.of(
      2.toByte, {
        var result = dao.config
          .insertProof(
            (
              ConfKeys.im_paideia_contracts_treasury,
              DAOConfigValueSerializer(treasuryContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_config,
              DAOConfigValueSerializer(configContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_action(
                actionSendFundsContract.ergoTree.bytes
              ),
              DAOConfigValueSerializer(actionSendFundsContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_action(
                actionUpdateConfigContract.ergoTree.bytes
              ),
              DAOConfigValueSerializer(actionUpdateConfigContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_proposal(
                proposalBasicContract.ergoTree.bytes
              ),
              DAOConfigValueSerializer(proposalBasicContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_staking_changestake,
              DAOConfigValueSerializer(stakingChangeContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_staking_stake,
              DAOConfigValueSerializer(stakingStakeContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_staking_compound,
              DAOConfigValueSerializer(stakingCompoundContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_staking_profitshare,
              DAOConfigValueSerializer(stakingProfitShareContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_staking_snapshot,
              DAOConfigValueSerializer(stakingSnapshotContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_staking_state,
              DAOConfigValueSerializer(stakeStateOutput.useContract.contractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_staking_vote,
              DAOConfigValueSerializer(stakingVoteContractSignature)
            ),
            (
              ConfKeys.im_paideia_contracts_staking_unstake,
              DAOConfigValueSerializer(stakingUnstakeContractSignature)
            )
          )(Left(configDigest))
        resultingDigest = Some(result._2)
        result._1
      }
    ),
    ContextVar.of(
      3.toByte,
      ErgoValueBuilder.buildFor(
        Colls.fromArray(
          Array(
            Colls.fromArray(DAOConfigValueSerializer(treasuryContractSignature)),
            Colls.fromArray(DAOConfigValueSerializer(configContractSignature)),
            Colls.fromArray(DAOConfigValueSerializer(actionSendFundsContractSignature)),
            Colls.fromArray(
              DAOConfigValueSerializer(actionUpdateConfigContractSignature)
            ),
            Colls.fromArray(DAOConfigValueSerializer(proposalBasicContractSignature)),
            Colls.fromArray(DAOConfigValueSerializer(stakingChangeContractSignature)),
            Colls.fromArray(DAOConfigValueSerializer(stakingStakeContractSignature)),
            Colls.fromArray(DAOConfigValueSerializer(stakingCompoundContractSignature)),
            Colls.fromArray(
              DAOConfigValueSerializer(stakingProfitShareContractSignature)
            ),
            Colls.fromArray(DAOConfigValueSerializer(stakingSnapshotContractSignature)),
            Colls.fromArray(
              DAOConfigValueSerializer(stakeStateOutput.useContract.contractSignature)
            ),
            Colls.fromArray(DAOConfigValueSerializer(stakingVoteContractSignature)),
            Colls.fromArray(DAOConfigValueSerializer(stakingUnstakeContractSignature))
          )
        )
      )
    ),
    ContextVar.of(
      4.toByte,
      ErgoValueBuilder.buildFor(
        Colls.fromArray(
          Array(
            Colls.fromArray(actionSendFundsContract.ergoTree.bytes),
            Colls.fromArray(actionUpdateConfigContract.ergoTree.bytes),
            Colls.fromArray(proposalBasicContract.ergoTree.bytes)
          )
        )
      )
    )
  )

  val contextVarsProtoDAO = List(
    ContextVar.of(0.toByte, 1.toByte),
    ContextVar.of(
      1.toByte,
      protoDAOInputBox.useContract.getConfigContext(Some(paideiaConfigDigest))
    ),
    ContextVar.of(
      2.toByte,
      ErgoValueBuilder.buildFor(Colls.fromArray(Array[Byte]()))
    )
  )

  val configOutput = configContract.box(_ctx, dao, resultingDigest)

  val stakeChangeO      = stakingChangeContract.box(_ctx).outBox
  val stakeStakeO       = stakingStakeContract.box(_ctx).outBox
  val stakeUnstakeO     = stakingUnstakeContract.box(_ctx).outBox
  val stakeCompoundO    = stakingCompoundContract.box(_ctx).outBox
  val stakeSnapshotO    = stakingSnapshotContract.box(_ctx).outBox
  val stakeVoteO        = stakingVoteContract.box(_ctx).outBox
  val stakeProfitShareO = stakingProfitShareContract.box(_ctx).outBox
  val createDaoO        = createDaoContract.box(_ctx, createDaoInput.getValue()).outBox
  fee           = 1000000
  changeAddress = _changeAddress
  inputs = List[InputBox](
    protoDAOInput.withContextVars(contextVarsProtoDAO: _*),
    createDaoInput.withContextVars(contextVarsCreateDAO: _*),
    proposalMintBox.withContextVars(proposalMintContext: _*),
    actionMintBox.withContextVars(actionMintContext: _*),
    daoKeyMintBox.withContextVars(daoKeyMintContext: _*),
    stakeStateMintBox.withContextVars(stakeStateMintContext: _*)
  )
  dataInputs = List[InputBox](paideiaConfigBox)
  outputs = List[OutBox](
    daoOriginOutput.outBox,
    configOutput.outBox,
    stakeStateOutput.outBox,
    stakeChangeO,
    stakeStakeO,
    stakeUnstakeO,
    stakeCompoundO,
    stakeSnapshotO,
    stakeVoteO,
    stakeProfitShareO,
    createDaoO
  )
}
