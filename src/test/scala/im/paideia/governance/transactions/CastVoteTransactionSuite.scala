package im.paideia.governance.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import im.paideia.DAO
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.contracts.Vote
import im.paideia.Paideia
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import im.paideia.common.contracts.Config
import im.paideia.staking.contracts.StakeState
import im.paideia.governance.contracts.CastVote
import im.paideia.governance.Proposal
import im.paideia.governance.VoteRecord
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.common.events.TransactionEvent
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.common.events.CreateTransactionsEvent

class CastVoteTransactionSuite extends PaideiaTestSuite {
  test("Cast Vote") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val daoKey            = Util.randomKey
        val config            = DAOConfig(daoKey)
        val daoGovTokenId     = Util.randomKey
        val proposalTokenId   = Util.randomKey
        val actionTokenId     = Util.randomKey
        val voteTokenId       = Util.randomKey
        val stakeStateTokenId = Util.randomKey

        val digest1 = config._config.digest

        config.set(ConfKeys.im_paideia_dao_name, "Test DAO")
        config
          .set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoGovTokenId).getBytes())
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes()
        )
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes()
        )
        config.set(
          ConfKeys.im_paideia_dao_vote_tokenid,
          ErgoId.create(voteTokenId).getBytes()
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes())
        config.set(
          ConfKeys.im_paideia_staking_state_tokenid,
          ErgoId.create(stakeStateTokenId).getBytes()
        )
        config.set(ConfKeys.im_paideia_staking_profit_tokenids, Array[Array[Byte]]())
        config.set(ConfKeys.im_paideia_staking_emission_delay, 4L)
        config.set(ConfKeys.im_paideia_staking_emission_amount, 100000L)
        config.set(ConfKeys.im_paideia_staking_cyclelength, 1000000L)
        config.set(ConfKeys.im_paideia_staking_profit_thresholds, Array(0L, 0L))
        val digest2 = config._config.digest
        val dao     = new DAO(daoKey, config)
        Paideia.addDAO(dao)
        dao.proposals(0) = Proposal(dao.key, 0)

        val voteContract = Vote(PaideiaContractSignature(daoKey = dao.key))
        config.set(ConfKeys.im_paideia_contracts_vote, voteContract.contractSignature)

        val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))
        val proposalBox = proposalContract.box(
          ctx,
          0,
          Array(0L, 0L),
          0L,
          ctx.createPreHeader().build().getTimestamp() + 3600000,
          -1
        )
        proposalContract.clearBoxes()
        proposalContract.newBox(proposalBox.inputBox(), false)

        val state    = TotalStakingState(dao.key, 0L)
        val stakeKey = Util.randomKey
        val voteKey  = Util.randomKey

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))

        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        val stakingState = stakingContract
          .emptyBox(
            ctx,
            dao,
            100000000L
          )

        stakingState.stake(stakeKey, 100L)

        val stakingStateBox = stakingState
          .inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val voteBox = voteContract.box(ctx, voteKey, stakeKey, 0L).inputBox()
        voteContract.clearBoxes()
        voteContract.newBox(voteBox, false)

        val castVoteContract = CastVote(PaideiaContractSignature(daoKey = dao.key))
        val castVoteBox = castVoteContract
          .box(ctx, voteKey, 0, VoteRecord(Array(100L, 0L)), dummyAddress)
          .ergoTransactionOutput()

        val dummyTx = (new ErgoTransaction()).addOutputsItem(castVoteBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }
}
