package im.paideia.common.transactions

import im.paideia.common.PaideiaSuite
import im.paideia.HttpClientTesting
import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoId
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.governance.contracts.ActionSendFundsBasic
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.governance.contracts.ActionUpdateConfig
import im.paideia.governance.contracts.ProposalBasic

class GarbageCollectTransactionSuite extends PaideiaTestSuite {
  test("Cleanup ActionSendFundsBasic") {
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
        val proposalTokenId = Util.randomKey
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        val dao = new DAO(daoKey, config)

        Paideia.addDAO(dao)

        val actionContract =
          ActionSendFundsBasic(PaideiaContractSignature(daoKey = dao.key))
        actionContract.clearBoxes()
        actionContract.newBox(
          actionContract.box(ctx, 0, 1, 10000, Array()).withCreationHeight(0).inputBox(),
          false
        )

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 1000L, 800000L)
        )
        eventResponse.exceptions.map(e => throw e)
        val unsigneds = eventResponse.unsignedTransactions

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

  test("Cleanup ActionUpdateConfig") {
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
        val proposalTokenId = Util.randomKey
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        val dao = new DAO(daoKey, config)

        Paideia.addDAO(dao)

        val actionContract =
          ActionUpdateConfig(PaideiaContractSignature(daoKey = dao.key))
        actionContract.clearBoxes()
        actionContract.newBox(
          actionContract
            .box(ctx, 0, 1, 10000L, List(), List(), List())
            .withCreationHeight(0)
            .inputBox(),
          false
        )

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 1000L, 800000L)
        )
        eventResponse.exceptions.map(e => throw e)
        val unsigneds = eventResponse.unsignedTransactions

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

  test("Cleanup ProposalBasic") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]

        PaideiaTestSuite.init(ctx)
        val daoKey = Util.randomKey
        val config = DAOConfig(daoKey)

        val proposalTokenId = Util.randomKey
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )

        config.set(
          ConfKeys.im_paideia_staking_state_tokenid,
          ErgoId.create(Util.randomKey).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        val dao = new DAO(daoKey, config)

        Paideia.addDAO(dao)

        val proposal = dao.newProposal(0, "Old proposal")

        val proposalContract =
          ProposalBasic(PaideiaContractSignature(daoKey = dao.key))
        proposalContract.clearBoxes()
        proposalContract.newBox(
          proposalContract
            .box(
              ctx,
              "Old proposal",
              0,
              Array(0L, 0L),
              0L,
              1000000L,
              1,
              Some(proposal.votes.digest)
            )
            .withCreationHeight(0)
            .inputBox(),
          false
        )

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 1000L, 800000L)
        )
        eventResponse.exceptions.map(e => throw e)
        val unsigneds = eventResponse.unsignedTransactions

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
