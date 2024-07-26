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
import im.paideia.util.ConfKeys
import im.paideia.common.contracts.Config
import org.ergoplatform.appkit.SignedInput
import org.ergoplatform.restapi.client.ErgoTransactionInput
import org.ergoplatform.appkit.impl.SignedTransactionImpl
import org.ergoplatform.appkit.impl.ScalaBridge._
import org.ergoplatform.appkit.impl.ScalaBridge

class CreateProtoDAOTransactionSuite extends PaideiaTestSuite {
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
            ctx                     = ctx,
            paideiaDaoConfig        = Paideia.getConfig(Env.paideiaDaoKey),
            daoName                 = "Test DAO",
            daoGovernanceTokenId    = Util.randomKey,
            stakePoolSize           = 0L,
            governanceType          = GovernanceType.DEFAULT,
            quorum                  = 20L,
            threshold               = 20L,
            stakingEmissionAmount   = 0L,
            stakingEmissionDelay    = 1L,
            stakingCycleLength      = 3600000L,
            stakingProfitSharePct   = 50,
            userAddress             = dummyAddress,
            pureParticipationWeight = 0.toByte,
            participationWeight     = 20.toByte,
            url                     = "test_dao",
            description             = "Test DAO is the best",
            logo                    = "http://logo.com",
            minProposalTime         = 86400000,
            banner                  = "http://banner.com",
            bannerEnabled           = true,
            footer                  = "",
            footerEnabled           = false,
            theme                   = "default"
          )
          .ergoTransactionOutput()
          .creationHeight(0)
        val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOProxyBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        val eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 50L))
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(eventResponse.unsignedTransactions(0).isInstanceOf[RefundTransaction])
        val signed = ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
      }
    })
  }

  test("Create proto DAO") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val dummyAddress =
          Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")
        val protoDAOProxyContract =
          ProtoDAOProxy(
            PaideiaContractSignature(daoKey = Env.paideiaDaoKey)
          )
        Paideia
          .getConfig(Env.paideiaDaoKey)
          .set(
            ConfKeys.im_paideia_contracts_protodaoproxy,
            protoDAOProxyContract.contractSignature
          )

        val configContract = Config(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
        configContract.clearBoxes()
        configContract.newBox(
          configContract.box(ctx, Paideia.getDAO(Env.paideiaDaoKey)).inputBox(),
          false
        )
        val protoDAOProxyBox = protoDAOProxyContract
          .box(
            ctx                     = ctx,
            paideiaDaoConfig        = Paideia.getConfig(Env.paideiaDaoKey),
            daoName                 = "Test DAO",
            daoGovernanceTokenId    = Util.randomKey,
            stakePoolSize           = 0L,
            governanceType          = GovernanceType.DEFAULT,
            quorum                  = 20L,
            threshold               = 20L,
            stakingEmissionAmount   = 0L,
            stakingEmissionDelay    = 1L,
            stakingCycleLength      = 3600000L,
            stakingProfitSharePct   = 50,
            userAddress             = dummyAddress,
            pureParticipationWeight = 0.toByte,
            participationWeight     = 20.toByte,
            url                     = "test_dao",
            description             = "Test DAO is the best",
            logo                    = "http://logo.com",
            minProposalTime         = 86400000,
            banner                  = "http://banner.com",
            bannerEnabled           = true,
            footer                  = "",
            footerEnabled           = false,
            theme                   = "default"
          )
          .ergoTransactionOutput()
        val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOProxyBox)
        Paideia.handleEvent(TransactionEvent(ctx, false, dummyTx))
        var eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(
          eventResponse.unsignedTransactions(0).isInstanceOf[CreateProtoDAOTransaction]
        )
        var signed = ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
          .asInstanceOf[SignedTransactionImpl]
        var followUp: ErgoTransaction =
          ScalaBridge.isoErgoTransaction.from(signed.getTx())
        Paideia.handleEvent(TransactionEvent(ctx, false, followUp))
        eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(
          eventResponse.unsignedTransactions(0).isInstanceOf[MintTransaction]
        )
        signed = ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
          .asInstanceOf[SignedTransactionImpl]
        followUp = ScalaBridge.isoErgoTransaction.from(signed.getTx())
        Paideia.handleEvent(TransactionEvent(ctx, true, followUp))
        eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(
          eventResponse.unsignedTransactions(0).isInstanceOf[MintTransaction]
        )
        signed = ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
          .asInstanceOf[SignedTransactionImpl]
        followUp = ScalaBridge.isoErgoTransaction.from(signed.getTx())
        Paideia.handleEvent(TransactionEvent(ctx, true, followUp))
        eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(
          eventResponse.unsignedTransactions(0).isInstanceOf[MintTransaction]
        )
        signed = ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
          .asInstanceOf[SignedTransactionImpl]
        followUp = ScalaBridge.isoErgoTransaction.from(signed.getTx())
        Paideia.handleEvent(TransactionEvent(ctx, true, followUp))
        eventResponse = Paideia.handleEvent(CreateTransactionsEvent(ctx, 0L, 0L))
        assert(eventResponse.unsignedTransactions.size === 1)
        assert(
          eventResponse.unsignedTransactions(0).isInstanceOf[CreateDAOTransaction]
        )
        signed = ctx
          .newProverBuilder()
          .build()
          .sign(eventResponse.unsignedTransactions(0).unsigned)
          .asInstanceOf[SignedTransactionImpl]
        followUp = ScalaBridge.isoErgoTransaction.from(signed.getTx())
      }
    })
  }
}
