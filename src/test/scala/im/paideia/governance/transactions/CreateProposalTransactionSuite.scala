package im.paideia.governance.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.governance.contracts.Vote
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.util.Env
import im.paideia.common.contracts.Config
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.governance.contracts.CreateProposal
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.governance.contracts.ActionSendFundsBasic
import sigmastate.eval.CostingBox
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.common.TransactionEvent
import im.paideia.governance.Proposal
import java.nio.charset.StandardCharsets

class CreateProposalTransactionSuite extends PaideiaTestSuite {
  test("Create proposal") {
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
        val dao = new DAO(daoKey, config)
        Paideia.addDAO(dao)

        val voteContract = Vote(PaideiaContractSignature(daoKey = dao.key))
        config.set(ConfKeys.im_paideia_contracts_vote, voteContract.contractSignature)

        val state    = TotalStakingState(dao.key, 0L)
        val stakeKey = Util.randomKey

        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

        val daoOriginContract =
          DAOOrigin(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
        daoOriginContract.newBox(
          daoOriginContract
            .box(ctx, dao, Long.MaxValue, Long.MaxValue, Long.MaxValue)
            .inputBox(),
          false
        )

        dao.proposals(0) = Proposal(daoKey, 0)

        val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = dao.key))

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

        val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))
        config.set(
          ConfKeys.im_paideia_contracts_proposal(proposalContract.ergoTree.bytes),
          proposalContract.contractSignature
        )
        val proposalBox = proposalContract.box(
          ctx,
          0,
          Array(0L, 0L),
          0L,
          ctx.createPreHeader().build().getTimestamp() + 3600000,
          -1.toShort
        )

        val actionContract =
          ActionSendFundsBasic(PaideiaContractSignature(daoKey = dao.key))
        config.set(
          ConfKeys.im_paideia_contracts_action(actionContract.ergoTree.bytes),
          actionContract.contractSignature
        )
        val actionBox = actionContract.box(
          ctx,
          0,
          1,
          ctx.createPreHeader().build().getTimestamp() - 3600000,
          Array(
            CostingBox(
              false,
              ctx
                .newTxBuilder()
                .outBoxBuilder()
                .contract(dummyAddress.toErgoContract())
                .tokens(new ErgoToken(Env.paideiaTokenId, 30L))
                .value(468000000L)
                .build()
                .convertToInputWith(Util.randomKey, 0.toShort)
                .asInstanceOf[InputBoxImpl]
                .getErgoBox()
            )
          )
        )

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        val createProposalContract =
          CreateProposal(PaideiaContractSignature(daoKey = dao.key))
        val createProposalBox = createProposalContract.box(
          ctx,
          proposalBox.box,
          Array(actionBox.box, actionBox.box),
          stakeKey,
          dummyAddress
        )

        val dummyTx = (new ErgoTransaction())
          .addOutputsItem(createProposalBox.ergoTransactionOutput())
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
