package im.paideia.governance.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import im.paideia.DAO
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.common.TransactionEvent
import im.paideia.governance.contracts.Mint
import im.paideia.util.Env

class CreateDAOTransactionSuite extends PaideiaTestSuite {
    test("Create DAO") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val daoKey = Util.randomKey
                val config = DAOConfig()
                val proposalTokenId = Util.randomKey
                val actionTokenId = Util.randomKey
                val voteTokenId = Util.randomKey
                config.set(ConfKeys.im_paideia_dao_name,"Test DAO")
                config.set(ConfKeys.im_paideia_dao_proposal_tokenid,ErgoId.create(proposalTokenId).getBytes())
                config.set(ConfKeys.im_paideia_dao_action_tokenid,ErgoId.create(actionTokenId).getBytes())
                config.set(ConfKeys.im_paideia_dao_vote_tokenid,ErgoId.create(voteTokenId).getBytes())
                config.set(ConfKeys.im_paideia_dao_key,ErgoId.create(daoKey).getBytes())
                Paideia.addDAO(new DAO(daoKey,config))
                val mintContract = Mint(PaideiaContractSignature("im.paideia.governance.contracts.Mint",daoKey=Env.paideiaDaoKey))
                val mintProposalBox = Mint(mintContract.contractSignature).box(ctx,proposalTokenId,Long.MaxValue,"Test DAO Proposal","Test DAO Proposal",0).ergoTransactionOutput()
                val mintActionBox = Mint(mintContract.contractSignature).box(ctx,actionTokenId,Long.MaxValue,"Test DAO Action","Test DAO Action",0).ergoTransactionOutput()
                val mintVoteBox = Mint(mintContract.contractSignature).box(ctx,voteTokenId,Long.MaxValue,"Test DAO Vote","Test DAO Vote",0).ergoTransactionOutput()
                val mintDaoKeyBox = Mint(mintContract.contractSignature).box(ctx,daoKey,1L,"Test DAO Key","Test DAO Key",0).ergoTransactionOutput()
                
                Paideia.handleEvent(TransactionEvent(ctx,false,(new ErgoTransaction())
                    .addOutputsItem(mintProposalBox)
                    .addOutputsItem(mintActionBox)
                    .addOutputsItem(mintVoteBox)
                    .addOutputsItem(mintDaoKeyBox)))

                val protoDAOContract = ProtoDAO(PaideiaContractSignature("im.paideia.governance.contracts.ProtoDAO",daoKey=Env.paideiaDaoKey))
                val protoDAOBox = ProtoDAO(protoDAOContract.contractSignature).box(ctx,Paideia.getDAO(daoKey),3000000L).ergoTransactionOutput()
                val dummyTx = (new ErgoTransaction()).addOutputsItem(protoDAOBox)
                val eventResponse = Paideia.handleEvent(TransactionEvent(ctx,false,dummyTx))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0))
            }
        })
    }
}
