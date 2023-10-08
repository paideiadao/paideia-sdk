package im.paideia.governance.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoId
import im.paideia.DAO
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import im.paideia.common.contracts.Config
import im.paideia.staking.contracts.StakeState
import org.ergoplatform.restapi.client.FullBlock
import org.ergoplatform.restapi.client.BlockHeader
import org.ergoplatform.restapi.client.BlockTransactions
import im.paideia.common.events.BlockEvent
import org.ergoplatform.restapi.client.Transactions
import im.paideia.governance.Proposal
import com.typesafe.config.ConfigList
import im.paideia.common.events.CreateTransactionsEvent
import sigmastate.exceptions.InterpreterException

class EvaluateProposalTransactionSuite extends PaideiaTestSuite {
  test("Evaluate basic proposal, quorum met") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val daoKey            = Util.randomKey
        val config            = DAOConfig(daoKey)
        val proposalTokenId   = Util.randomKey
        val actionTokenId     = Util.randomKey
        val stakeStateTokenId = Util.randomKey
        val daoGovTokenId     = Util.randomKey
        config.set(ConfKeys.im_paideia_dao_name, "Test DAO")
        config
          .set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoGovTokenId).getBytes)
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        config.set(
          ConfKeys.im_paideia_staking_state_tokenid,
          ErgoId.create(stakeStateTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_staking_profit_tokenids, Array[Array[Byte]]())
        config.set(ConfKeys.im_paideia_staking_emission_delay, 4L)
        config.set(ConfKeys.im_paideia_staking_emission_amount, 100000L)
        config.set(ConfKeys.im_paideia_staking_cyclelength, 1000000L)
        config.set(ConfKeys.im_paideia_staking_profit_thresholds, Array(0L, 0L))
        config.set(ConfKeys.im_paideia_dao_quorum, 100L)
        config.set(ConfKeys.im_paideia_dao_threshold, 500L)
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        val dao = new DAO(daoKey, config)
        Paideia.addDAO(dao)

        dao.proposals(0) = Proposal(dao.key, 0, "test")

        val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))

        val paiRef = Paideia._actorList

        val state    = TotalStakingState(dao.key, 100000L, true)
        val stakeKey = Util.randomKey

        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(stakeKey, 1000L)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)
        val proposalBox =
          proposalContract.box(ctx, "test", 0, Array(1000L, 0L), 1000L, 1000L, -1)
        proposalContract.newBox(proposalBox.inputBox(), false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, proposalBox.endTime + 1000L, 0L)
        )
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Evaluate basic proposal, quorum not met") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val daoKey            = Util.randomKey
        val config            = DAOConfig(daoKey)
        val proposalTokenId   = Util.randomKey
        val actionTokenId     = Util.randomKey
        val stakeStateTokenId = Util.randomKey
        val daoGovTokenId     = Util.randomKey
        config.set(ConfKeys.im_paideia_dao_name, "Test DAO")
        config
          .set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoGovTokenId).getBytes)
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        config.set(
          ConfKeys.im_paideia_staking_state_tokenid,
          ErgoId.create(stakeStateTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_staking_profit_tokenids, Array[Array[Byte]]())
        config.set(ConfKeys.im_paideia_staking_emission_delay, 4L)
        config.set(ConfKeys.im_paideia_staking_emission_amount, 100000L)
        config.set(ConfKeys.im_paideia_staking_cyclelength, 1000000L)
        config.set(ConfKeys.im_paideia_staking_profit_thresholds, Array(0L, 0L))
        config.set(ConfKeys.im_paideia_dao_quorum, 100L)
        config.set(ConfKeys.im_paideia_dao_threshold, 500L)
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        val dao = new DAO(daoKey, config)
        Paideia.addDAO(dao)

        dao.proposals(0) = Proposal(dao.key, 0, "test")

        val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))

        val paiRef = Paideia._actorList

        val state    = TotalStakingState(dao.key, 100000L, true)
        val stakeKey = Util.randomKey

        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(stakeKey, 1000L)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)
        val proposalBox =
          proposalContract.box(ctx, "test", 0, Array(1L, 0L), 1L, 1000L, -1)
        proposalContract.newBox(proposalBox.inputBox(), false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, proposalBox.endTime + 1000L, 0L)
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
