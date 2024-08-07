package im.paideia.staking.transactions

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking._
import org.ergoplatform.sdk.ErgoToken
import im.paideia.staking.transactions._
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.InputBox
import im.paideia.staking.boxes._
import scala.util.Random
import im.paideia.DAOConfig
import im.paideia.util.Util
import im.paideia.common.PaideiaTestSuite
import im.paideia.util.ConfKeys
import im.paideia.staking.contracts.StakeState
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Config
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.Paideia
import im.paideia.common.events.TransactionEvent
import im.paideia.common.contracts.Treasury
import im.paideia.util.Env
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.staking.contracts.StakeCompound

class CompoundTransactionSuite extends PaideiaTestSuite {

  test("Sign compound tx on 1 staker") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao   = StakingTest.testDAO
        val state = TotalStakingState(dao.key, 0L)

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(
            ConfKeys.im_paideia_contracts_staking_state,
            stakingContract.contractSignature
          )

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(Util.randomKey, 1000000000000L)

        Range(0, dao.config[Long](ConfKeys.im_paideia_staking_emission_delay).toInt)
          .foreach(_ => stakingState.emit(9999999999999999L, 99999999999L))
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)
        treasuryContract.clearBoxes()
        treasuryContract.newBox(
          treasuryContract
            .box(
              ctx,
              dao.config,
              1000000000L,
              List(new ErgoToken(Env.paideiaTokenId, 10000000L))
            )
            .inputBox(),
          false
        )

        val compoundContract = StakeCompound(PaideiaContractSignature(daoKey = dao.key))
        compoundContract.newBox(compoundContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .ergoTransactionOutput()
        stakingContract.clearBoxes()

        val dummyTx = (new ErgoTransaction()).addOutputsItem(stakingStateBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        eventResponse.exceptions.map(e => throw e)
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Sign compound tx on 1000 stakers out of 10000") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao    = StakingTest.testDAO
        val sigUsd = Util.randomKey
        val state  = TotalStakingState(dao.key, 0L)

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(
            ConfKeys.im_paideia_contracts_staking_state,
            stakingContract.contractSignature
          )

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )
        Range(0, 10000).foreach(_ => stakingState.stake(Util.randomKey, 100L, true))
        Range(0, dao.config[Long](ConfKeys.im_paideia_staking_emission_delay).toInt)
          .foreach(_ => stakingState.emit(9999999999999999L, 9999999L))
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)
        treasuryContract.clearBoxes()
        treasuryContract.newBox(
          treasuryContract
            .box(
              ctx,
              dao.config,
              1000000000L,
              List(new ErgoToken(Env.paideiaTokenId, 10000000L))
            )
            .inputBox(),
          false
        )

        val compoundContract = StakeCompound(PaideiaContractSignature(daoKey = dao.key))
        compoundContract.newBox(compoundContract.box(ctx).inputBox(), false)

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .ergoTransactionOutput()
        stakingContract.clearBoxes()

        val dummyTx = (new ErgoTransaction()).addOutputsItem(stakingStateBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        eventResponse.exceptions.map(e => throw e)
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Sign compound tx on 1 staker with second staker missing") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao       = StakingTest.testDAO
        val state     = TotalStakingState(dao.key, 0L)
        val randomKey = Util.randomKey

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(
            ConfKeys.im_paideia_contracts_staking_state,
            stakingContract.contractSignature
          )

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(Util.randomKey, 1000000000000L)
        stakingState.stake(randomKey, 1000000000000L)

        Range(0, dao.config[Long](ConfKeys.im_paideia_staking_emission_delay).toInt)
          .foreach(_ => stakingState.emit(9999999999999999L, 99999999999L))
        val currentStake = stakingState.getStake(randomKey)
        currentStake.stake   = 0L
        currentStake.rewards = currentStake.rewards.map(_ => 0L)
        stakingState.unstake(randomKey, currentStake, List[ErgoToken]())
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)
        treasuryContract.clearBoxes()
        treasuryContract.newBox(
          treasuryContract
            .box(
              ctx,
              dao.config,
              1000000000L,
              List(new ErgoToken(Env.paideiaTokenId, 10000000L))
            )
            .inputBox(),
          false
        )

        val compoundContract = StakeCompound(PaideiaContractSignature(daoKey = dao.key))
        compoundContract.newBox(compoundContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .ergoTransactionOutput()
        stakingContract.clearBoxes()

        val dummyTx = (new ErgoTransaction()).addOutputsItem(stakingStateBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        eventResponse.exceptions.map(e => throw e)
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Sign compound tx on 0 staker with all stakers missing") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao        = StakingTest.testDAO
        val state      = TotalStakingState(dao.key, 0L)
        val randomKey  = Util.randomKey
        val randomKey2 = Util.randomKey

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(
            ConfKeys.im_paideia_contracts_staking_state,
            stakingContract.contractSignature
          )

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(randomKey2, 1000000000000L)
        stakingState.stake(randomKey, 1000000000000L)

        Range(0, dao.config[Long](ConfKeys.im_paideia_staking_emission_delay).toInt)
          .foreach(_ => stakingState.emit(9999999999999999L, 99999999999L))
        val currentStake = stakingState.getStake(randomKey)
        currentStake.stake   = 0L
        currentStake.rewards = currentStake.rewards.map(_ => 0L)
        stakingState.unstake(randomKey, currentStake, List[ErgoToken]())
        stakingState.unstake(randomKey2, currentStake, List[ErgoToken]())
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)
        treasuryContract.clearBoxes()
        treasuryContract.newBox(
          treasuryContract
            .box(
              ctx,
              dao.config,
              1000000000L,
              List(new ErgoToken(Env.paideiaTokenId, 10000000L))
            )
            .inputBox(),
          false
        )

        val compoundContract = StakeCompound(PaideiaContractSignature(daoKey = dao.key))
        compoundContract.newBox(compoundContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .ergoTransactionOutput()
        stakingContract.clearBoxes()

        val dummyTx = (new ErgoTransaction()).addOutputsItem(stakingStateBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        eventResponse.exceptions.map(e => throw e)
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Sign compound tx on 1 staker with profit") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao   = StakingTest.testDAO
        val state = TotalStakingState(dao.key, 0L)

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(
            ConfKeys.im_paideia_contracts_staking_state,
            stakingContract.contractSignature
          )

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(Util.randomKey, 1000000000000L)

        Range(0, dao.config[Long](ConfKeys.im_paideia_staking_emission_delay).toInt - 1)
          .foreach(_ => stakingState.emit(9999999999999999L, 99999999999L))

        stakingState.profitShare(List(10000L, 10000L), List())
        stakingState.emit(9999999999999999L, 99999999999L)
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)
        treasuryContract.clearBoxes()
        treasuryContract.newBox(
          treasuryContract
            .box(
              ctx,
              dao.config,
              1000000000L,
              List(new ErgoToken(Env.paideiaTokenId, 10000000L))
            )
            .inputBox(),
          false
        )

        val compoundContract = StakeCompound(PaideiaContractSignature(daoKey = dao.key))
        compoundContract.newBox(compoundContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .ergoTransactionOutput()
        stakingContract.clearBoxes()

        val dummyTx = (new ErgoTransaction()).addOutputsItem(stakingStateBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        eventResponse.exceptions.map(e => throw e)
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }
}
