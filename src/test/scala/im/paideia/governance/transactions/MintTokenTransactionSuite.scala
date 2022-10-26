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
import im.paideia.common.TransactionEvent
import im.paideia.DAOConfig
import im.paideia.DAO
import org.ergoplatform.appkit.ErgoId

class MintTokenTransactionSuite extends PaideiaTestSuite{
    test("Mint first") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val daoKey = Util.randomKey
                val config = DAOConfig()
                config.set("im.paideia.dao.name","Test DAO")
                Paideia.addDAO(new DAO(daoKey,config))
                val protoDAOContract = ProtoDAO(PaideiaContractSignature("im.paideia.governance.contracts.ProtoDAO",daoKey=Env.paideiaDaoKey))
                val protoDAOBox = ProtoDAO(protoDAOContract.contractSignature).box(ctx,Paideia.getDAO(daoKey),3000000L).ergoTransactionOutput()
                val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOBox)
                val paideiaRef = Paideia._actorList
                val eventResponse = Paideia.handleEvent(TransactionEvent(ctx,false,dummyTx))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0))
            }
        })
    }

    test("Mint second") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val daoKey = Util.randomKey
                val config = DAOConfig()
                config.set("im.paideia.dao.name","Test DAO")
                config.set("im.paideia.dao.vote.tokenid",ErgoId.create(Util.randomKey).getBytes())
                Paideia.addDAO(new DAO(daoKey,config))
                val protoDAOContract = ProtoDAO(PaideiaContractSignature("im.paideia.governance.contracts.ProtoDAO",daoKey=Env.paideiaDaoKey))
                val protoDAOBox = ProtoDAO(protoDAOContract.contractSignature).box(ctx,Paideia.getDAO(daoKey),3000000L).ergoTransactionOutput()
                val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOBox)
                val paideiaRef = Paideia._actorList
                val eventResponse = Paideia.handleEvent(TransactionEvent(ctx,false,dummyTx))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0))
            }
        })
    }
}
