package im.paideia.governance.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.BlockchainContext
import im.paideia.governance.contracts.ProtoDAOProxy
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.util.Env
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.TransactionEvent
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.util.Util
import im.paideia.governance.GovernanceType
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.transactions.RefundTransaction
import org.ergoplatform.appkit.Address

class CreateProtoDAOTransactionSuite extends PaideiaTestSuite {
  test("Create proto DAO") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")
        val protoDAOProxyContract =
          ProtoDAOProxy(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
        val protoDAOProxyBox = protoDAOProxyContract
          .box(
            ctx                   = ctx,
            paideiaDaoConfig      = Paideia.getConfig(Env.paideiaDaoKey),
            daoName               = "Test DAO",
            daoGovernanceTokenId  = Util.randomKey,
            stakePoolSize         = 0L,
            governanceType        = GovernanceType.DEFAULT,
            quorum                = 20,
            threshold             = 20,
            stakingEmissionAmount = 0L,
            stakingEmissionDelay  = 1,
            stakingCycleLength    = 3600000L,
            stakingProfitSharePct = 50,
            dummyAddress,
            0.toByte,
            20.toByte
          )
          .ergoTransactionOutput()
        val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOProxyBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(
          eventResponse.unsignedTransactions(0).isInstanceOf[CreateProtoDAOTransaction]
        )
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Refund proto DAO") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")
        val protoDAOProxyContract =
          ProtoDAOProxy(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
        val protoDAOProxyBox = protoDAOProxyContract
          .box(
            ctx                   = ctx,
            paideiaDaoConfig      = Paideia.getConfig(Env.paideiaDaoKey),
            daoName               = "Test DAO",
            daoGovernanceTokenId  = Util.randomKey,
            stakePoolSize         = 0L,
            governanceType        = GovernanceType.DEFAULT,
            quorum                = 20,
            threshold             = 20,
            stakingEmissionAmount = 0L,
            stakingEmissionDelay  = 1,
            stakingCycleLength    = 3600000L,
            stakingProfitSharePct = 50,
            dummyAddress,
            0.toByte,
            20.toByte
          )
          .ergoTransactionOutput()
          .creationHeight(0)
        val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOProxyBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 50L))
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(eventResponse.unsignedTransactions(0).isInstanceOf[RefundTransaction])
        ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }
}
