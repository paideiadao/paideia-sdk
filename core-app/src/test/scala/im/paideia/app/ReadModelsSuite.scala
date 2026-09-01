package im.paideia.app

import im.paideia.DAO
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.common.PaideiaTestSuite
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.Proposal
import im.paideia.governance.contracts.ActionSendFundsBasic
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.staking.StakingTest
import im.paideia.staking.TotalStakingState
import im.paideia.staking.contracts.StakeState
import im.paideia.util.ConfKeys
import im.paideia.util.Env
import im.paideia.util.Util
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.sdk.ErgoToken
import sigma.data.CBox

/** Builds a DAO with one proposal and one send-funds action - the same box-construction
  * pattern `CreateProposalTransactionSuite`
  * (`src/test/scala/im/paideia/governance/transactions/CreateProposalTransactionSuite.scala`)
  * uses, registering each box as confirmed directly via its contract instance's `newBox`
  * (the same registration a live replay ends up performing once a create-proposal bot
  * transaction confirms - see the comment below), then covers [[ReadModels]]'s three
  * queries against that state.
  */
class ReadModelsSuite extends PaideiaTestSuite {

  test(
    "daoList/proposalList/proposalDetail decode a proposal's name, tallies and send-funds outputs"
  ) {
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
        val stakeStateTokenId = Util.randomKey

        config.set(ConfKeys.im_paideia_dao_name, "Test DAO")
        config.set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoGovTokenId).getBytes)
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        config.set(
          ConfKeys.im_paideia_staking_state_tokenid,
          ErgoId.create(stakeStateTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_staking_emission_delay, 4L)
        config.set(ConfKeys.im_paideia_staking_emission_amount, 100000L)
        config.set(ConfKeys.im_paideia_staking_cyclelength, 1000000L)

        val dao = new DAO(daoKey, config)
        Paideia.addDAO(dao)

        TotalStakingState(dao.key, 0L)
        val stakeKey = Util.randomKey

        val recipient =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

        val daoOriginContract = DAOOrigin(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_dao, daoOriginContract.contractSignature)
        daoOriginContract.newBox(
          daoOriginContract.box(ctx, dao, Long.MaxValue, Long.MaxValue).inputBox(),
          false
        )

        dao.proposals(0) = Proposal(daoKey, 0, "test")

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        val stakingState    = stakingContract.emptyBox(ctx, dao, 100000000L)
        stakingState.stake(stakeKey, 100L)
        val stakingStateBox = stakingState.inputBox()
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingStateBox, false)

        val endTime          = ctx.createPreHeader().build().getTimestamp() + 86500000L
        val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))
        config.set(
          ConfKeys.im_paideia_contracts_proposal(proposalContract.ergoTree.bytes),
          proposalContract.contractSignature
        )
        val proposalBox = proposalContract.box(
          ctx,
          "test",
          0,
          Array(0L, 0L),
          0L,
          endTime,
          -1.toShort
        )

        val actionContract =
          ActionSendFundsBasic(PaideiaContractSignature(daoKey = dao.key))
        config.set(
          ConfKeys.im_paideia_contracts_action(actionContract.ergoTree.bytes),
          actionContract.contractSignature
        )
        val actionActivationTime =
          ctx.createPreHeader().build().getTimestamp() - 86400000L
        val actionBox = actionContract.box(
          ctx,
          0,
          1,
          actionActivationTime,
          Array(
            CBox(
              ctx
                .newTxBuilder()
                .outBoxBuilder()
                .contract(recipient.toErgoContract())
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
        config.set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        // Registers the proposal/action boxes directly as confirmed UTXOs of their own
        // contract instances - the same `newBox` call a live replay ends up making once a
        // create-proposal bot transaction confirms (PaideiaContract.handleEvent's output
        // scanning), without needing to build/sign/confirm that whole CreateProposal proxy
        // transaction here too; ReadModels only cares that the boxes are confirmed, not how
        // they got that way.
        proposalContract.newBox(proposalBox.inputBox(), false)
        actionContract.newBox(actionBox.inputBox(), false)

        // ReadModels' queries resolve config values via DAOConfig.apply, which looks up
        // the config AVL+ tree at a specific digest (read off the confirmed config box) -
        // that digest-addressed lookup only works against committed tree state, so the
        // queued config.set(...) mutations above must actually be drained/committed first,
        // the same way a real replay's periodic Paideia.commit() does before any read-model
        // query ever runs against it.
        Paideia.commit()

        // --- daoList ---
        val daos = ReadModels.daoList()
        val ours = daos.find(_.key == daoKey)
        assert(ours.isDefined, s"expected $daoKey in ${daos.map(_.key)}")
        assert(ours.get.name == "Test DAO")
        assert(ours.get.configBoxCreationHeight >= 0)

        // --- proposalList ---
        val proposals = ReadModels.proposalList(ctx, daoKey)
        assert(proposals.size == 1, proposals)
        val summary = proposals.head
        assert(summary.index == 0)
        assert(summary.name == "test")
        assert(summary.endTime == endTime)
        assert(summary.totalVotes == 0L)
        assert(summary.voteCounts == List(0L, 0L))
        assert(summary.passed == -1)

        // --- proposalDetail ---
        val detail = ReadModels.proposalDetail(ctx, daoKey, 0)
        assert(detail.summary == summary)
        assert(detail.actions.size == 1, detail.actions)
        detail.actions.head match {
          case sf: SendFundsActionView =>
            assert(sf.optionId == 1L)
            assert(sf.activationTime == actionActivationTime)
            assert(sf.outputs.size == 1)
            val output = sf.outputs.head
            assert(output.address == recipient.toString)
            assert(output.nanoErg == 468000000L)
            assert(output.tokens == List((Env.paideiaTokenId, 30L)))
          case other => fail(s"expected a SendFundsActionView, got $other")
        }
        assert(detail.votes.isEmpty)
      }
    })
  }

  test("stakeStatus finds a known stake key and skips unknown candidates") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)

        val dao        = StakingTest.testDAO
        val stakeKey   = Util.randomKey
        val unknownKey = Util.randomKey

        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        val stakingState    = stakingContract.emptyBox(ctx, dao, 100000000L)
        stakingState.stake(stakeKey, 250L)
        stakingContract.clearBoxes()
        stakingContract.newBox(stakingState.inputBox(), false)

        val found = ReadModels.stakeStatus(ctx, dao.key, Set(stakeKey, unknownKey))
        assert(found.size == 1, found)
        assert(found.head.stakeKey == ErgoId.create(stakeKey).toString())
        assert(found.head.stake.stake == 250L)
        assert(found.head.participation.isEmpty)

        assert(ReadModels.stakeStatus(ctx, dao.key, Set(unknownKey)).isEmpty)
        assert(ReadModels.stakeStatus(ctx, dao.key, Set.empty).isEmpty)
      }
    })
  }
}
