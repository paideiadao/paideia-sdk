package im.paideia.governance.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.BlockchainContext
import im.paideia.governance.contracts.ProtoDAOProxy
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.util.Env
import im.paideia.common.PaideiaEvent
import im.paideia.common.TransactionEvent
import org.ergoplatform.restapi.client.ErgoTransaction

class CreateProtoDAOTransactionSuite extends PaideiaTestSuite {
    test("Create proto DAO") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val protoDAOProxyContract = ProtoDAOProxy(PaideiaContractSignature("im.paideia.governance.contracts.ProtoDAOProxy"))
                val protoDAOProxyBox = ProtoDAOProxy(protoDAOProxyContract.contractSignature).box(ctx,Paideia.getConfig(Env.paideiaDaoKey)).ergoTransactionOutput()
                val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOProxyBox)
                val paideiaRef = Paideia._actorList
                val eventResponse = Paideia.handleEvent(TransactionEvent(ctx,false,dummyTx))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0))
            }
        })
    }
}
