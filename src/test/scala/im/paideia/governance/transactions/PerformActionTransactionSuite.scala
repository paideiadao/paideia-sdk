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
import im.paideia.Paideia
import im.paideia.common.contracts.Treasury
import im.paideia.governance.Proposal
import im.paideia.governance.contracts.ProposalBasic
import org.ergoplatform.appkit.Address
import im.paideia.common.contracts.Config
import org.ergoplatform.appkit.ErgoToken
import im.paideia.governance.contracts.ActionSendFundsBasic
import org.ergoplatform.appkit.impl.InputBoxImpl
import sigmastate.eval.CostingBox
import org.ergoplatform.restapi.client.FullBlock
import org.ergoplatform.restapi.client.BlockHeader
import org.ergoplatform.restapi.client.BlockTransactions
import org.ergoplatform.restapi.client.Transactions
import im.paideia.common.events.BlockEvent
import im.paideia.governance.contracts.ActionUpdateConfig
import im.paideia.DAOConfigKey
import im.paideia.DAOConfigValueSerializer
import im.paideia.util.Env
import im.paideia.common.events.CreateTransactionsEvent

class PerformActionTransactionSuite extends PaideiaTestSuite {
  test("Send funds") {
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
        val dao = new DAO(daoKey, config)

        val testToken = Util.randomKey

        Paideia.addDAO(dao)

        val paiRef = Paideia._daoMap

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        treasuryContract.clearBoxes()
        treasuryContract.newBox(
          treasuryContract
            .box(ctx, dao.config, 233000000L, List(new ErgoToken(Util.randomKey, 20L)))
            .inputBox(),
          false
        )
        treasuryContract.newBox(
          treasuryContract.box(ctx, dao.config, 2000000L, List[ErgoToken]()).inputBox(),
          false
        )
        treasuryContract.newBox(
          treasuryContract
            .box(ctx, dao.config, 233000000L, List(new ErgoToken(testToken, 20L)))
            .inputBox(),
          false
        )
        treasuryContract.newBox(
          treasuryContract
            .box(
              ctx,
              dao.config,
              2000000L,
              List(new ErgoToken(testToken, 20L), new ErgoToken(Util.randomKey, 10L))
            )
            .inputBox(),
          false
        )
        config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)
        dao.newProposal(0)

        val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))
        val proposalBox = proposalContract.box(
          ctx,
          0,
          Array(0L, 100L),
          0L,
          ctx.createPreHeader().build().getTimestamp() - 3600000,
          1.toShort
        )
        proposalContract.clearBoxes()
        proposalContract.newBox(proposalBox.inputBox(), false)

        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

        val actionContract =
          ActionSendFundsBasic(PaideiaContractSignature(daoKey = dao.key))
        val actionBox = actionContract.box(
          ctx,
          0,
          1,
          ctx.createPreHeader().build().getTimestamp() - 3600000,
          Array(
            CostingBox(
              ctx
                .newTxBuilder()
                .outBoxBuilder()
                .contract(dummyAddress.toErgoContract())
                .tokens(new ErgoToken(testToken, 30L))
                .value(468000000L)
                .build()
                .convertToInputWith(Util.randomKey, 0.toShort)
                .asInstanceOf[InputBoxImpl]
                .getErgoBox()
            )
          )
        )
        actionContract.clearBoxes()
        actionContract.newBox(actionBox.inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, proposalBox.endTime + 1000L, 0L)
        )
        eventResponse.exceptions.map(e => throw e)
        assert(eventResponse.unsignedTransactions.size === 1)
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Add config") {
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
        val dao = new DAO(daoKey, config)

        val testToken = Util.randomKey

        Paideia.addDAO(dao)

        dao.newProposal(0)

        val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))
        val proposalBox = proposalContract.box(
          ctx,
          0,
          Array(0L, 100L),
          0L,
          ctx.createPreHeader().build().getTimestamp() - 3600000,
          1.toShort
        )
        proposalContract.clearBoxes()
        proposalContract.newBox(proposalBox.inputBox(), false)

        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

        val actionContract =
          ActionUpdateConfig(PaideiaContractSignature(daoKey = dao.key))
        val actionBox = actionContract.box(
          ctx,
          0,
          1,
          ctx.createPreHeader().build().getTimestamp() - 3600000,
          List[DAOConfigKey](),
          List[(DAOConfigKey, Array[Byte])](),
          List(
            (
              ConfKeys.im_paideia_staking_profit_tokenids,
              DAOConfigValueSerializer(
                Array(ErgoId.create(Env.paideiaTokenId).getBytes())
              )
            )
          )
        )
        actionContract.clearBoxes()
        actionContract.newBox(actionBox.inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        config
          .set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

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

  test("Update config") {
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
        val dao = new DAO(daoKey, config)

        val testToken = Util.randomKey

        Paideia.addDAO(dao)

        dao.newProposal(0)

        val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))
        val proposalBox = proposalContract.box(
          ctx,
          0,
          Array(0L, 100L),
          0L,
          ctx.createPreHeader().build().getTimestamp() - 3600000,
          1.toShort
        )
        proposalContract.clearBoxes()
        proposalContract.newBox(proposalBox.inputBox(), false)

        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

        val actionContract =
          ActionUpdateConfig(PaideiaContractSignature(daoKey = dao.key))
        val actionBox = actionContract.box(
          ctx,
          0,
          1,
          ctx.createPreHeader().build().getTimestamp() - 3600000,
          List[DAOConfigKey](),
          List(
            (
              ConfKeys.im_paideia_dao_name,
              DAOConfigValueSerializer(
                "Test DAO with a better name"
              )
            )
          ),
          List[(DAOConfigKey, Array[Byte])]()
        )
        actionContract.clearBoxes()
        actionContract.newBox(actionBox.inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        config
          .set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

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

  test("Remove config") {
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
        val dao = new DAO(daoKey, config)

        val testToken = Util.randomKey

        Paideia.addDAO(dao)

        dao.newProposal(0)

        val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))
        val proposalBox = proposalContract.box(
          ctx,
          0,
          Array(0L, 100L),
          0L,
          ctx.createPreHeader().build().getTimestamp() - 3600000,
          1.toShort
        )
        proposalContract.clearBoxes()
        proposalContract.newBox(proposalBox.inputBox(), false)

        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

        val actionContract =
          ActionUpdateConfig(PaideiaContractSignature(daoKey = dao.key))
        val actionBox = actionContract.box(
          ctx,
          0,
          1,
          ctx.createPreHeader().build().getTimestamp() - 3600000,
          List(ConfKeys.im_paideia_dao_name),
          List[(DAOConfigKey, Array[Byte])](),
          List[(DAOConfigKey, Array[Byte])]()
        )
        actionContract.clearBoxes()
        actionContract.newBox(actionBox.inputBox(), false)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        config
          .set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

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
