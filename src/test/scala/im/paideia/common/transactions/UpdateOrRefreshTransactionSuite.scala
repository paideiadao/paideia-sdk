package im.paideia.common.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoId
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.util.Env
import im.paideia.staking.contracts.StakeState
import im.paideia.staking.TotalStakingState
import im.paideia.staking.StakingTest

class UpdateOrRefreshTransactionSuite extends PaideiaTestSuite {
  test("Refresh config") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]

        PaideiaTestSuite.init(ctx)
        val daoKey = Util.randomKey
        val config = DAOConfig(daoKey)

        val actionTokenId = Util.randomKey
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        val dao = new DAO(daoKey, config)

        Paideia.addDAO(dao)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        config.set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.clearBoxes()
        configContract.newBox(
          configContract.box(ctx, dao).withCreationHeight(0).inputBox(),
          false
        )

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 1000L, 600000L)
        )
        eventResponse.exceptions.map(e => throw e)
        val unsigneds = eventResponse.unsignedTransactions
          .filter(pt =>
            pt match {
              case UpdateOrRefreshTransaction(
                    _ctx,
                    outdatedBoxes,
                    longLivingKey,
                    _dao,
                    newAddress,
                    operatorAddress
                  ) =>
                dao == _dao && longLivingKey == ConfKeys.im_paideia_contracts_config.originalKey.get
              case _: PaideiaTransaction => false
            }
          )
        assert(
          unsigneds.size === 1
        )
        ctx
          .newProverBuilder()
          .build()
          .sign(unsigneds(0).unsigned)
      }
    })
  }

  test("Refresh dao origin") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]

        PaideiaTestSuite.init(ctx)
        val daoKey = Util.randomKey
        val config = DAOConfig(daoKey)

        val actionTokenId   = Util.randomKey
        val proposalTokenId = Util.randomKey
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        val dao = new DAO(daoKey, config)

        Paideia.addDAO(dao)

        val daoOriginContract =
          DAOOrigin(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
        daoOriginContract.newBox(
          daoOriginContract
            .box(ctx, dao, Long.MaxValue, Long.MaxValue)
            .withCreationHeight(0)
            .inputBox(),
          false
        )

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 1000L, 600000L)
        )
        eventResponse.exceptions.map(e => throw e)
        val unsigneds = eventResponse.unsignedTransactions
          .filter(pt =>
            pt match {
              case UpdateOrRefreshTransaction(
                    _ctx,
                    outdatedBoxes,
                    longLivingKey,
                    _dao,
                    newAddress,
                    operatorAddress
                  ) =>
                Paideia.getDAO(
                  Env.paideiaDaoKey
                ) == _dao && longLivingKey == ConfKeys.im_paideia_contracts_dao.originalKey.get
              case _: PaideiaTransaction => false
            }
          )
        assert(
          unsigneds.size === 1
        )
        ctx
          .newProverBuilder()
          .build()
          .sign(unsigneds(0).unsigned)
      }
    })
  }

  test("Refresh stake state") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]

        PaideiaTestSuite.init(ctx)
        val dao           = StakingTest.testDAO
        val config        = dao.config
        val actionTokenId = Util.randomKey
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(dao.key).getBytes)

        val stakeStateContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        config.set(
          ConfKeys.im_paideia_contracts_staking_state,
          stakeStateContract.contractSignature
        )
        stakeStateContract.newBox(
          stakeStateContract.emptyBox(ctx, dao, 0L).withCreationHeight(0).inputBox(),
          false
        )

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        config.set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.clearBoxes()
        configContract.newBox(
          configContract.box(ctx, dao).inputBox(),
          false
        )

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 0L, 600000L)
        )
        eventResponse.exceptions.map(e => throw e)
        val unsigneds = eventResponse.unsignedTransactions
          .filter(pt =>
            pt match {
              case UpdateOrRefreshTransaction(
                    _ctx,
                    outdatedBoxes,
                    longLivingKey,
                    _dao,
                    newAddress,
                    operatorAddress
                  ) =>
                dao == _dao && longLivingKey == ConfKeys.im_paideia_contracts_staking_state.originalKey.get
              case _: PaideiaTransaction => false
            }
          )
        assert(
          unsigneds.size === 1
        )
        ctx
          .newProverBuilder()
          .build()
          .sign(unsigneds(0).unsigned)
      }
    })
  }
}
