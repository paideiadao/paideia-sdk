package im.paideia.staking.transactions

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.boxes._
import org.ergoplatform.appkit.ErgoToken
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.InputBox
import im.paideia.staking.transactions._
import im.paideia.DAOConfig
import im.paideia.util.Util
import im.paideia.common.PaideiaTestSuite
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.events.TransactionEvent
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Config
import im.paideia.staking.contracts.StakeState
import im.paideia.staking.contracts.AddStakeProxy
import im.paideia.staking.StakingTest
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.staking.contracts.ChangeStake
import im.paideia.common.transactions.RefundTransaction

class AddStakeTransactionSuite extends PaideiaTestSuite {
  test("Sign add stake tx") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao   = StakingTest.testDAO
        val state = TotalStakingState(dao.key)

        val testKey = Util.randomKey

        val dummyAddress = Address.create("4MQyML64GnzMxZgm")

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(
            ConfKeys.im_paideia_contracts_staking_state,
            stakingContract.contractSignature
          )

        val changeStakeContract = ChangeStake(PaideiaContractSignature(daoKey = dao.key))
        changeStakeContract.newBox(changeStakeContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = configContract.box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(testKey, 100L)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val addStakeProxyContract =
          AddStakeProxy(PaideiaContractSignature(daoKey = dao.key))
        val addStakeProxyBox = addStakeProxyContract
          .box(ctx, testKey, 3000000L, dummyAddress.toString())
          .ergoTransactionOutput()
        val dummyTx = (new ErgoTransaction()).addOutputsItem(addStakeProxyBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        eventResponse.exceptions.map(e => throw e)
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(eventResponse.unsignedTransactions(0).isInstanceOf[AddStakeTransaction])
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Refund add stake tx") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao   = StakingTest.testDAO
        val state = TotalStakingState(dao.key)

        val testKey = Util.randomKey

        val dummyAddress = Address.create("4MQyML64GnzMxZgm")

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(
            ConfKeys.im_paideia_contracts_staking_state,
            stakingContract.contractSignature
          )

        val changeStakeContract = ChangeStake(PaideiaContractSignature(daoKey = dao.key))
        changeStakeContract.newBox(changeStakeContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = configContract.box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(testKey, 100L)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val addStakeProxyContract =
          AddStakeProxy(PaideiaContractSignature(daoKey = dao.key))
        val addStakeProxyBox = addStakeProxyContract
          .box(ctx, testKey, 3000000L, dummyAddress.toString())
          .ergoTransactionOutput()
          .creationHeight(0)
        val dummyTx = (new ErgoTransaction()).addOutputsItem(addStakeProxyBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 50L))
        eventResponse.exceptions.map(e => throw e)
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(eventResponse.unsignedTransactions(0).isInstanceOf[RefundTransaction])
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }
}
