package im.paideia.governance.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.Env
import im.paideia.util.Util
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.common.events.TransactionEvent
import im.paideia.DAOConfig
import im.paideia.DAO
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.common.events.CreateTransactionsEvent

class MintTokenTransactionSuite extends PaideiaTestSuite {
  test("Mint first") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val daoKey     = Util.randomKey
        val config     = DAOConfig(daoKey)
        val daoTokenId = Util.randomKey
        config.set(ConfKeys.im_paideia_dao_name, "Test DAO")
        config.set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoTokenId).getBytes())
        Paideia.addDAO(new DAO(daoKey, config))
        val protoDAOContract =
          ProtoDAO(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
        val protoDAOBox = protoDAOContract
          .box(ctx, Paideia.getDAO(daoKey), 0L, value = 3000000L)
          .ergoTransactionOutput()
        val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOBox)
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

  test("Mint second") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val daoKey     = Util.randomKey
        val config     = DAOConfig(daoKey)
        val daoTokenId = Util.randomKey
        config.set(ConfKeys.im_paideia_dao_name, "Test DAO")
        config.set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoTokenId).getBytes())
        config.set(
          ConfKeys.im_paideia_dao_vote_tokenid,
          ErgoId.create(Util.randomKey).getBytes()
        )
        Paideia.addDAO(new DAO(daoKey, config))
        val protoDAOContract =
          ProtoDAO(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
        val protoDAOBox = protoDAOContract
          .box(ctx, Paideia.getDAO(daoKey), 0L, value = 3000000L)
          .ergoTransactionOutput()
        val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOBox)
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
