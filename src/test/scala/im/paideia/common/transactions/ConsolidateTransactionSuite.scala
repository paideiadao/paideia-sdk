package im.paideia.common.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoId
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.contracts.Treasury
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.sdk.ErgoToken
import im.paideia.common.contracts.Config
import im.paideia.common.events.CreateTransactionsEvent
import org.ergoplatform.appkit.InputBox
import scala.collection.JavaConverters._

class ConsolidateTransactionSuite extends PaideiaTestSuite {
  test("Consolidate treasury") {
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
          .set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoGovTokenId).getBytes)
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        config.set(
          ConfKeys.im_paideia_staking_state_tokenid,
          ErgoId.create(stakeStateTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
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
        treasuryContract.newBox(
          treasuryContract
            .box(ctx, dao.config, 233000000L, List(new ErgoToken(testToken, 20L)))
            .inputBox(),
          false
        )
        config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        config.set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 1000L, 0L)
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

  test("Consolidate skips oversized box") {
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
        config
          .set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoGovTokenId).getBytes)
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        config.set(
          ConfKeys.im_paideia_staking_state_tokenid,
          ErgoId.create(stakeStateTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        val dao = new DAO(daoKey, config)

        Paideia.addDAO(dao)

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        treasuryContract.clearBoxes()

        // 8 small, dust-like boxes holding 0-2 tokens each.
        (0 until 8).foreach { (i: Int) =>
          val tokens: List[ErgoToken] =
            if (i % 3 == 0) List[ErgoToken]()
            else if (i % 3 == 1) List(new ErgoToken(Util.randomKey, 20L))
            else
              List(new ErgoToken(Util.randomKey, 20L), new ErgoToken(Util.randomKey, 10L))
          treasuryContract.newBox(
            treasuryContract.box(ctx, dao.config, 10000000L, tokens).inputBox(),
            false
          )
        }

        // 1 large box holding ~60 distinct tokens - this is the one that must be
        // excluded from consolidation because the merged output would exceed the
        // node's box size limit.
        val largeBoxTokens: List[ErgoToken] =
          List.tabulate(60)(_ => new ErgoToken(Util.randomKey, 20L))
        val largeBoxInputBox: InputBox = treasuryContract
          .box(ctx, dao.config, 100000000L, largeBoxTokens)
          .inputBox()
        treasuryContract.newBox(largeBoxInputBox, false)

        config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        config.set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 1000L, 0L)
        )
        eventResponse.exceptions.map(e => throw e)
        assert(eventResponse.unsignedTransactions.size === 1)

        val unsignedTx = eventResponse.unsignedTransactions(0).unsigned
        val inputIds: Set[String] =
          unsignedTx.getInputs().asScala.map((b: InputBox) => b.getId().toString()).toSet

        assert(!inputIds.contains(largeBoxInputBox.getId().toString()))
        assert(inputIds.size >= 5)

        ctx.newProverBuilder().build().sign(unsignedTx)
      }
    })
  }

  test("No valid consolidation yields no transaction") {
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
        config
          .set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoGovTokenId).getBytes)
        config.set(
          ConfKeys.im_paideia_dao_proposal_tokenid,
          ErgoId.create(proposalTokenId).getBytes
        )
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        config.set(
          ConfKeys.im_paideia_staking_state_tokenid,
          ErgoId.create(stakeStateTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        val dao = new DAO(daoKey, config)

        Paideia.addDAO(dao)

        val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))
        treasuryContract.clearBoxes()

        // 3 small boxes holding 0-2 tokens each.
        (0 until 3).foreach { (i: Int) =>
          val tokens: List[ErgoToken] =
            if (i == 0) List[ErgoToken]()
            else if (i == 1) List(new ErgoToken(Util.randomKey, 20L))
            else
              List(new ErgoToken(Util.randomKey, 20L), new ErgoToken(Util.randomKey, 10L))
          treasuryContract.newBox(
            treasuryContract.box(ctx, dao.config, 10000000L, tokens).inputBox(),
            false
          )
        }

        // 2 large boxes holding ~60 distinct tokens each - with only 5 boxes total,
        // any subset with the required minimum of 5 inputs must include both of these,
        // pushing the merged output well over the node's box size limit.
        (0 until 2).foreach { (_: Int) =>
          val largeBoxTokens: List[ErgoToken] =
            List.tabulate(60)(_ => new ErgoToken(Util.randomKey, 20L))
          treasuryContract.newBox(
            treasuryContract.box(ctx, dao.config, 100000000L, largeBoxTokens).inputBox(),
            false
          )
        }

        config
          .set(ConfKeys.im_paideia_contracts_treasury, treasuryContract.contractSignature)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        config.set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 1000L, 0L)
        )
        assert(eventResponse.exceptions.isEmpty)
        assert(eventResponse.unsignedTransactions.size === 0)
      }
    })
  }
}
