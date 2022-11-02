package im.paideia.staking.transactions

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.boxes._
import org.ergoplatform.appkit.ErgoToken
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.InputBox
import im.paideia.staking.transactions._
import im.paideia.DAOConfig
import im.paideia.util.Util
import im.paideia.common.PaideiaTestSuite
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.TransactionEvent
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Config
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.staking.contracts.AddStakeProxy

class AddStakeTransactionSuite extends PaideiaTestSuite {
    test("Sign add stake tx") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val actorRef = Paideia._actorList
                val daoKey = Util.randomKey
                val config = DAOConfig()
                val stakeStateTokenId = Util.randomKey
                val daoTokenId = Util.randomKey
                
                config.set(ConfKeys.im_paideia_dao_name,"Test DAO")
                config.set(ConfKeys.im_paideia_staking_state_tokenid,ErgoId.create(stakeStateTokenId).getBytes())
                config.set(ConfKeys.im_paideia_staking_profit_tokenids,Array[Array[Byte]]())
                config.set(ConfKeys.im_paideia_staking_emission_delay,4L)
                config.set(ConfKeys.im_paideia_dao_tokenid,ErgoId.create(daoTokenId).getBytes())
                config.set(ConfKeys.im_paideia_staking_emission_amount,100000L)
                config.set(ConfKeys.im_paideia_staking_cyclelength,1000000L)
                config.set(ConfKeys.im_paideia_staking_profit_thresholds,Array(0L,0L))

                val dao = new DAO(daoKey,config)
                Paideia.addDAO(dao)
                val state = TotalStakingState(daoKey, 0L)

                val testKey = Util.randomKey
                state.stake(testKey,100L)

                val dummyAddress = Address.create("4MQyML64GnzMxZgm")

                val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = daoKey))
                config.set(ConfKeys.im_paideia_contracts_staking,stakingContract.contractSignature)

                val configContract = Config(PaideiaContractSignature(daoKey = daoKey))
                
                val configBox = Config(configContract.contractSignature).box(ctx,dao).inputBox()
                configContract.newBox(configBox,false)

                
                val stakingStateBox = PlasmaStaking(stakingContract.contractSignature).box(
                    ctx,
                    config,
                    state,
                    100000000L
                ).inputBox()

                stakingContract.newBox(stakingStateBox,false)

                val addStakeProxyContract = AddStakeProxy(PaideiaContractSignature(daoKey=daoKey))
                val addStakeProxyBox = AddStakeProxy(addStakeProxyContract.contractSignature).box(ctx,testKey,3000000L,dummyAddress.toString()).ergoTransactionOutput()
                val dummyTx = (new ErgoTransaction()).addOutputsItem(addStakeProxyBox)
                val eventResponse = Paideia.handleEvent(TransactionEvent(ctx,false,dummyTx))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0))
            }
        })
    }
}
