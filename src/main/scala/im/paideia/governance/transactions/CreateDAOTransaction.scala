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
import sigma.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scorex.crypto.authds.ADDigest
import sigma.AvlTree
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
import scorex.util.encode.Base16
import sigma.serialization.ErgoTreeSerializer
import sigma.ast.ByteArrayConstant
import sigma.ast.ErgoTree
import org.ergoplatform.appkit.AppkitHelpers

case class CreateDAOTransaction(
  _ctx: BlockchainContextImpl,
  protoDAOInput: InputBox,
  dao: DAO,
  _changeAddress: Address
) extends PaideiaTransaction {
  ctx = _ctx
  val protoDAOInputBox = ProtoDAOBox.fromInputBox(_ctx, protoDAOInput)

  val configDigest =
    Some(
      ADDigest @@ protoDAOInput
        .getRegisters()
        .get(0)
        .getValue()
        .asInstanceOf[AvlTree]
        .digest
        .toArray
    )

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
      new ErgoId(
        dao.config.getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid, configDigest)
      )
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val proposalMintBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(
        dao.config.getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid, configDigest)
      )
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val daoKeyMintBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_key, configDigest))
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val stakeStateMintBox = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(
        dao.config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid, configDigest)
      )
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)

  val createDaoContract =
    CreateDAO(ConfKeys.im_paideia_contracts_createdao, Env.paideiaDaoKey)

  val createDaoInput =
    createDaoContract.boxes(createDaoContract.getUtxoSet.toArray.apply(0))

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
        ConfKeys.im_paideia_contracts_createdao
      )(Some(paideiaConfigDigest))
  )

  val proposalMintContext = List(
    mintPaideiaConfigProof,
    ContextVar
      .of(
        1.toByte,
        dao.config.getProof(ConfKeys.im_paideia_dao_proposal_tokenid)(configDigest)
      ),
    ContextVar.of(2.toByte, ConfKeys.im_paideia_dao_proposal_tokenid.ergoValue)
  )

  val actionMintContext = List(
    mintPaideiaConfigProof,
    ContextVar.of(
      1.toByte,
      dao.config.getProof(ConfKeys.im_paideia_dao_action_tokenid)(configDigest)
    ),
    ContextVar.of(2.toByte, ConfKeys.im_paideia_dao_action_tokenid.ergoValue)
  )

  val daoKeyMintContext = List(
    mintPaideiaConfigProof,
    ContextVar
      .of(1.toByte, dao.config.getProof(ConfKeys.im_paideia_dao_key)(configDigest)),
    ContextVar.of(2.toByte, ConfKeys.im_paideia_dao_key.ergoValue)
  )

  val stakeStateMintContext = List(
    mintPaideiaConfigProof,
    ContextVar
      .of(
        1.toByte,
        dao.config.getProof(ConfKeys.im_paideia_staking_state_tokenid)(configDigest)
      ),
    ContextVar.of(2.toByte, ConfKeys.im_paideia_staking_state_tokenid.ergoValue)
  )

  var resultingDigest: Option[ADDigest] = None

  val insertOperations = createDaoContract.getInsertOperations(dao)

  val emissionTime = _ctx.createPreHeader().build().getTimestamp() + dao.config[Long](
    ConfKeys.im_paideia_staking_cyclelength,
    configDigest
  )

  val state = TotalStakingState(
    dao.key,
    emissionTime
  )

  var result = dao.config
    .insertProof(
      insertOperations: _*
    )(Left(configDigest.get))
  resultingDigest = Some(result._2)

  val daoOriginContract =
    DAOOrigin(ConfKeys.im_paideia_contracts_dao, dao.key, resultingDigest)

  val daoOriginOutput = daoOriginContract.box(
    _ctx,
    dao,
    Long.MaxValue,
    Long.MaxValue
  )

  val configContract =
    Config(ConfKeys.im_paideia_contracts_config, dao.key, resultingDigest)

  val stakeStateOutput =
    StakeState(ConfKeys.im_paideia_contracts_staking_state, dao.key, resultingDigest)
      .emptyBox(_ctx, dao, protoDAOInputBox.stakePool)

  val treasuryContract =
    Treasury(ConfKeys.im_paideia_contracts_treasury, dao.key, resultingDigest)
  val actionSendFundsContract = ActionSendFundsBasic(
    paideiaConfig[PaideiaContractSignature](
      ConfKeys.im_paideia_default_action_sendfunds_signature
    ).withDaoKey(dao.key)
  )

  val actionUpdateConfigContract = ActionUpdateConfig(
    paideiaConfig[PaideiaContractSignature](
      ConfKeys.im_paideia_default_action_updateconfig_signature
    ).withDaoKey(dao.key)
  )
  val proposalBasicContract = ProposalBasic(
    paideiaConfig[PaideiaContractSignature](
      ConfKeys.im_paideia_default_proposal_basic_signature
    ).withDaoKey(dao.key)
  )
  val stakingChangeContract = ChangeStake(
    ConfKeys.im_paideia_contracts_staking_changestake,
    dao.key,
    resultingDigest
  )
  val stakingStakeContract =
    Stake(ConfKeys.im_paideia_contracts_staking_stake, dao.key, resultingDigest)
  val stakingCompoundContract = StakeCompound(
    ConfKeys.im_paideia_contracts_staking_compound,
    dao.key,
    resultingDigest
  )
  val stakingProfitShareContract = StakeProfitShare(
    ConfKeys.im_paideia_contracts_staking_profitshare,
    dao.key,
    resultingDigest
  )
  val stakingSnapshotContract = StakeSnapshot(
    ConfKeys.im_paideia_contracts_staking_snapshot,
    dao.key,
    resultingDigest
  )
  val stakingVoteContract =
    StakeVote(ConfKeys.im_paideia_contracts_staking_vote, dao.key, resultingDigest)
  val stakingUnstakeContract =
    Unstake(ConfKeys.im_paideia_contracts_staking_unstake, dao.key, resultingDigest)

  val contextVarsCreateDAO = List(
    ContextVar.of(
      0.toByte,
      createDaoContract.getConfigContext(Some(paideiaConfigDigest))
    ),
    ContextVar.of(
      1.toByte,
      createDaoContract.getDAOConfigContext(dao.config, configDigest)
    ),
    ContextVar.of(
      2.toByte,
      result._1
    ),
    ContextVar.of(
      3.toByte,
      ErgoValueBuilder.buildFor(
        Colls.fromArray(
          Array(
            Colls.fromArray(DAOConfigValueSerializer(treasuryContract.contractSignature)),
            Colls.fromArray(DAOConfigValueSerializer(configContract.contractSignature)),
            Colls.fromArray(
              DAOConfigValueSerializer(actionSendFundsContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(actionUpdateConfigContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(proposalBasicContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(stakingChangeContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(stakingStakeContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(stakingCompoundContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(stakingProfitShareContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(stakingSnapshotContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(stakeStateOutput.useContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(stakingVoteContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(stakingUnstakeContract.contractSignature)
            ),
            Colls.fromArray(
              DAOConfigValueSerializer(daoOriginContract.contractSignature)
            )
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
    ),
    ContextVar.of(
      5.toByte,
      ErgoValueBuilder.buildFor(createDaoContract.getAvlTreeKeys)
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
  val stakeStakeO       = stakingStakeContract.box(_ctx, 1000000L).outBox
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
