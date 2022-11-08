package im.paideia.governance.transactions

import im.paideia.common.PaideiaTestSuite
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.util.Env
import im.paideia.common.contracts.Config
import im.paideia.DAO
import im.paideia.util.Util
import im.paideia.Paideia
import im.paideia.DAOConfig
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.BlockchainContext
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.staking.TotalStakingState
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.governance.contracts.CreateVoteProxy
import org.ergoplatform.appkit.Address
import im.paideia.common.TransactionEvent
import im.paideia.governance.contracts.Vote

class CreateVoteTransactionSuite extends PaideiaTestSuite{
    test("Create Vote") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val daoKey = Util.randomKey
                val config = DAOConfig()
                val daoGovTokenId = Util.randomKey
                val proposalTokenId = Util.randomKey
                val actionTokenId = Util.randomKey
                val voteTokenId = Util.randomKey
                val stakeStateTokenId = Util.randomKey
                config.set(ConfKeys.im_paideia_dao_name,"Test DAO")
                config.set(ConfKeys.im_paideia_dao_tokenid,ErgoId.create(daoGovTokenId).getBytes())
                config.set(ConfKeys.im_paideia_dao_proposal_tokenid,ErgoId.create(proposalTokenId).getBytes())
                config.set(ConfKeys.im_paideia_dao_action_tokenid,ErgoId.create(actionTokenId).getBytes())
                config.set(ConfKeys.im_paideia_dao_vote_tokenid,ErgoId.create(voteTokenId).getBytes())
                config.set(ConfKeys.im_paideia_dao_key,ErgoId.create(daoKey).getBytes())
                config.set(ConfKeys.im_paideia_staking_state_tokenid,ErgoId.create(stakeStateTokenId).getBytes())
                config.set(ConfKeys.im_paideia_staking_profit_tokenids,Array[Array[Byte]]())
                config.set(ConfKeys.im_paideia_staking_emission_delay,4L)
                config.set(ConfKeys.im_paideia_staking_emission_amount,100000L)
                config.set(ConfKeys.im_paideia_staking_cyclelength,1000000L)
                config.set(ConfKeys.im_paideia_staking_profit_thresholds,Array(0L,0L))
                val dao = new DAO(daoKey,config)
                val voteContract = Vote(PaideiaContractSignature(daoKey=dao.key))
                config.set(ConfKeys.im_paideia_contracts_vote,voteContract.contractSignature)
                Paideia.addDAO(dao)

                val state = TotalStakingState(dao.key,0L)
                val stakeKey = Util.randomKey

                state.stake(stakeKey,100L)

                val dummyAddress = Address.create("9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b")

                val daoOriginContract = DAOOrigin(PaideiaContractSignature(daoKey=Env.paideiaDaoKey))
                daoOriginContract.newBox(daoOriginContract.box(ctx,dao,Long.MaxValue,Long.MaxValue,Long.MaxValue).inputBox(),false)

                val configContract = Config(PaideiaContractSignature(daoKey=dao.key))
                configContract.newBox(configContract.box(ctx,dao).inputBox(),false)

                val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = dao.key))

                val stakingStateBox = stakingContract.box(
                    ctx,
                    dao.config,
                    state,
                    100000000L
                ).inputBox()
                stakingContract.clearBoxes()
                stakingContract.newBox(stakingStateBox,false)

                val createVoteProxyContract = CreateVoteProxy(PaideiaContractSignature(daoKey=dao.key))
                val createVoteBox = createVoteProxyContract.box(ctx,stakeKey,dummyAddress).ergoTransactionOutput()

                val dummyTx = (new ErgoTransaction()).addOutputsItem(createVoteBox)
                val eventResponse = Paideia.handleEvent(TransactionEvent(ctx,false,dummyTx))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0))
            }
        })
    }
}
