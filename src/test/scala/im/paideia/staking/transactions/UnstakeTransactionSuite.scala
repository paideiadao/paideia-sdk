package im.paideia.staking.transactions

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking._
import org.ergoplatform.appkit.ErgoToken
import im.paideia.staking.transactions._
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.InputBox
import im.paideia.staking.boxes._
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.common.PaideiaTestSuite
import im.paideia.staking.contracts.UnstakeProxy
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.common.contracts.Config
import im.paideia.util.ConfKeys
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.Paideia
import im.paideia.common.events.TransactionEvent

class UnstakeTransactionSuite extends PaideiaTestSuite {
  test("Sign partial unstake tx") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao   = StakingTest.testDAO
        val state = TotalStakingState(dao.key, 0L)

        val testKey = Util.randomKey

        val dummyAddress = Address.create("4MQyML64GnzMxZgm")

        val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_staking, stakingContract.contractSignature)

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(testKey, 10000L)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val currentStake = stakingState.getStake(testKey)

        currentStake.stake -= 300L

        val unstakeProxyContract =
          UnstakeProxy(PaideiaContractSignature(daoKey = dao.key))
        val unstakeProxyBox = unstakeProxyContract
          .box(ctx, testKey, currentStake, dummyAddress.toString())
          .ergoTransactionOutput()
        val dummyTx       = (new ErgoTransaction()).addOutputsItem(unstakeProxyBox)
        val eventResponse = Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Sign full unstake tx") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao   = StakingTest.testDAO
        val state = TotalStakingState(dao.key, 0L)

        val testKey = Util.randomKey

        val dummyAddress = Address.create("4MQyML64GnzMxZgm")

        val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_staking, stakingContract.contractSignature)

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(testKey, 10000L)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)
        val currentStake = stakingState.getStake(testKey)
        currentStake.clear
        val unstakeProxyContract =
          UnstakeProxy(PaideiaContractSignature(daoKey = dao.key))
        val unstakeProxyBox = unstakeProxyContract
          .box(ctx, testKey, currentStake, dummyAddress.toString())
          .ergoTransactionOutput()
        val dummyTx       = (new ErgoTransaction()).addOutputsItem(unstakeProxyBox)
        val eventResponse = Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }
}
