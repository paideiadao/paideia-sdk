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
          ConfKeys.im_paideia_dao_vote_tokenid,
          ErgoId.create(voteTokenId).getBytes
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
}
