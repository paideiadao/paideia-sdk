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
import im.paideia.staking.boxes._
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfig
import im.paideia.staking.transactions._
import im.paideia.common.PaideiaTestSuite
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.staking.contracts.StakeState
import im.paideia.util.ConfKeys
import im.paideia.common.contracts.Config
import im.paideia.staking.contracts.SplitProfit
import org.ergoplatform.restapi.client.FullBlock
import org.ergoplatform.restapi.client.BlockHeader
import org.ergoplatform.restapi.client.BlockTransactions
import org.ergoplatform.restapi.client.Transactions
import im.paideia.Paideia
import im.paideia.common.events.BlockEvent
import im.paideia.util.Util
import org.ergoplatform.appkit.ErgoId
import im.paideia.common.contracts.Treasury
import im.paideia.DAOConfigValueSerializer
import im.paideia.DAOConfigValueDeserializer
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.staking.contracts.StakeProfitShare

class ProfitShareTransactionSuite extends PaideiaTestSuite {
  test("Profit share erg tx on empty state") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao   = StakingTest.testDAO
        val state = TotalStakingState(dao.key, 0L, true)

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

        dao.config.set(ConfKeys.im_paideia_staking_profit_share_pct, 50.toByte)
        dao.config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(Util.randomKey).getBytes()
        )
        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)

        val profitShareContract =
          StakeProfitShare(PaideiaContractSignature(daoKey = dao.key))
        profitShareContract.newBox(profitShareContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val splitProfitContract = SplitProfit(PaideiaContractSignature(daoKey = dao.key))
        val splitProfitBox = splitProfitContract.box(ctx, 100000000L, List[ErgoToken]())
        splitProfitContract.newBox(splitProfitBox.inputBox(), false)

        val stakingStateBox = stakingState.inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(
            ctx,
            stakingState.nextEmission - 1000L,
            ctx.getHeight() + 30
          )
        )
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Profit share staked token tx on empty state") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao   = StakingTest.testDAO
        val state = TotalStakingState(dao.key, 0L, true)

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
        dao.config.set(ConfKeys.im_paideia_staking_profit_share_pct, 50.toByte)
        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)
        val sigUsd = Util.randomKey
        dao.config.set(
          ConfKeys.im_paideia_staking_profit_tokenids,
          Array(ErgoId.create(sigUsd).getBytes())
        )

        val profitShareContract =
          StakeProfitShare(PaideiaContractSignature(daoKey = dao.key))
        profitShareContract.newBox(profitShareContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val splitProfitContract = SplitProfit(PaideiaContractSignature(daoKey = dao.key))
        val splitProfitBox = splitProfitContract.box(
          ctx,
          3000000L,
          List[ErgoToken](
            new ErgoToken(
              dao.config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),
              1000000L
            )
          )
        )
        splitProfitContract.newBox(splitProfitBox.inputBox(), false)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(
            ctx,
            stakingState.nextEmission - 1000L,
            ctx.getHeight() + 30
          )
        )
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Profit share whitelisted token tx on empty state") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao   = StakingTest.testDAO
        val state = TotalStakingState(dao.key, 0L, true)

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
        dao.config.set(ConfKeys.im_paideia_staking_profit_share_pct, 50.toByte)
        dao.config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(Util.randomKey).getBytes()
        )
        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)
        val sigUsd = Util.randomKey
        dao.config.set(
          ConfKeys.im_paideia_staking_profit_tokenids,
          Array(ErgoId.create(sigUsd).getBytes())
        )

        val profitShareContract =
          StakeProfitShare(PaideiaContractSignature(daoKey = dao.key))
        profitShareContract.newBox(profitShareContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val splitProfitContract = SplitProfit(PaideiaContractSignature(daoKey = dao.key))
        val splitProfitBox = splitProfitContract
          .box(ctx, 3000000L, List[ErgoToken](new ErgoToken(sigUsd, 1000000L)))
        splitProfitContract.newBox(splitProfitBox.inputBox(), false)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val dummyBlock = (new FullBlock())
          .header(
            new BlockHeader()
              .timestamp(stakingState.nextEmission - 1000L)
              .height(ctx.getHeight() + 30)
          )
          .blockTransactions(new BlockTransactions().transactions(new Transactions()))
        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(
            ctx,
            stakingState.nextEmission - 1000L,
            ctx.getHeight() + 30
          )
        )
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }
}
