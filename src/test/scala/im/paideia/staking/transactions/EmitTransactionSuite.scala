package im.paideia.staking.transactions

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking._
import org.ergoplatform.sdk.ErgoToken
import im.paideia.staking.transactions._
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfig
import im.paideia.staking.boxes._
import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.restapi.client.FullBlock
import org.ergoplatform.restapi.client.BlockHeader
import im.paideia.common.events.BlockEvent
import im.paideia.Paideia
import im.paideia.staking.contracts._
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.util.ConfKeys
import org.ergoplatform.restapi.client.BlockTransactions
import org.ergoplatform.restapi.client.Transactions
import im.paideia.util.Util
import org.ergoplatform.sdk.ErgoId
import im.paideia.common.contracts.Treasury
import im.paideia.util.Env
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.governance.VoteRecord

class EmitTransactionSuite extends PaideiaTestSuite {
  test("Sign emit tx on empty state") {
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

        val snapshotContract = StakeSnapshot(PaideiaContractSignature(daoKey = dao.key))
        snapshotContract.newBox(snapshotContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
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

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, stakingState.nextEmission + 1000L, 0L)
        )
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Sign emit tx on non-empty state") {
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

        stakingState.stake(Util.randomKey, 100000L)

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
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

        val snapshotContract = StakeSnapshot(PaideiaContractSignature(daoKey = dao.key))
        snapshotContract.newBox(snapshotContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, stakingState.nextEmission + 1000L, 0L)
        )
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Sign emit tx on non-empty state with profit") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao    = StakingTest.testDAO
        val state  = TotalStakingState(dao.key, 0L)
        val sigUsd = Util.randomKey
        dao.config.set(
          ConfKeys.im_paideia_staking_profit_tokenids,
          Array(ErgoId.create(sigUsd).getBytes)
        )

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

        stakingState.stake(Util.randomKey, 100000L)

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
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

        stakingState
          .profitShare(List(0L, 1000L, 1000L), List(new ErgoToken(sigUsd, 1000L)))

        val snapshotContract = StakeSnapshot(PaideiaContractSignature(daoKey = dao.key))
        snapshotContract.newBox(snapshotContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, stakingState.nextEmission + 1000L, 0L)
        )
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Sign emit tx on non-empty state with profit and participation") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dao        = StakingTest.testDAO
        val state      = TotalStakingState(dao.key, 0L)
        val prop       = dao.newProposal(0, "test")
        val voteRecord = VoteRecord(Array(0L, 100000L))

        val stakeKey = Util.randomKey

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

        val currentVoteProof = prop.votes.lookUp(ErgoId.create(stakeKey))
        prop.votes.insertWithDigest((ErgoId.create(stakeKey), voteRecord))(Right(10000))

        stakingState.stake(stakeKey, 100000L)
        stakingState.vote(stakeKey, 12676873625498375L, currentVoteProof, voteRecord)

        val sigUsd = Util.randomKey
        dao.config.set(
          ConfKeys.im_paideia_staking_profit_tokenids,
          Array(ErgoId.create(sigUsd).getBytes)
        )

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
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

        stakingState
          .profitShare(List(0L, 1000L, 1000L), List(new ErgoToken(sigUsd, 1000L)))

        val snapshotContract = StakeSnapshot(PaideiaContractSignature(daoKey = dao.key))
        snapshotContract.newBox(snapshotContract.box(ctx).inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))

        val configBox = Config(configContract.contractSignature).box(ctx, dao).inputBox()
        configContract.clearBoxes()
        configContract.newBox(configBox, false)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, stakingState.nextEmission + 1000L, 0L)
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
