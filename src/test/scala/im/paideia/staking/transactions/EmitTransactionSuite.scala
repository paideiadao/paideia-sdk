package im.paideia.staking.transactions

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking._
import org.ergoplatform.appkit.ErgoToken
import im.paideia.staking.transactions._
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfig
import im.paideia.staking.boxes._
import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.restapi.client.FullBlock
import org.ergoplatform.restapi.client.BlockHeader
import im.paideia.common.BlockEvent
import im.paideia.Paideia
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.util.ConfKeys
import org.ergoplatform.restapi.client.BlockTransactions
import org.ergoplatform.restapi.client.Transactions
import im.paideia.util.Util

class EmitTransactionSuite extends PaideiaTestSuite {
    test("Sign emit tx on empty state") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val dao = StakingTest.testDAO
                val state = TotalStakingState(dao.key,0L,true)

                val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = dao.key))
                dao.config.set(ConfKeys.im_paideia_contracts_staking,stakingContract.contractSignature)

                val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
                
                val configBox = Config(configContract.contractSignature).box(ctx,dao).inputBox()
                configContract.clearBoxes()
                configContract.newBox(configBox,false)

                
                val stakingStateBox = stakingContract.box(
                    ctx,
                    dao.config,
                    state,
                    100000000L
                ).inputBox()
                stakingContract.clearBoxes()
                stakingContract.newBox(stakingStateBox,false)

                val dummyBlock = (new FullBlock()).header(new BlockHeader().timestamp(state.nextEmission+1000L)).blockTransactions(new BlockTransactions().transactions(new Transactions()))
                val eventResponse = Paideia.handleEvent(BlockEvent(ctx,dummyBlock))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0))
            }
        })
    }

    test("Sign emit tx on non-empty state") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val dao = StakingTest.testDAO
                val state = TotalStakingState(dao.key,0L)
                state.stake(Util.randomKey,100000L)

                val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = dao.key))
                dao.config.set(ConfKeys.im_paideia_contracts_staking,stakingContract.contractSignature)

                val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
                
                val configBox = Config(configContract.contractSignature).box(ctx,dao).inputBox()
                configContract.clearBoxes()
                configContract.newBox(configBox,false)

                
                val stakingStateBox = stakingContract.box(
                    ctx,
                    dao.config,
                    state,
                    100000000L
                ).inputBox()
                stakingContract.clearBoxes()
                stakingContract.newBox(stakingStateBox,false)

                val dummyBlock = (new FullBlock()).header(new BlockHeader().timestamp(state.nextEmission+1000L)).blockTransactions(new BlockTransactions().transactions(new Transactions()))
                val eventResponse = Paideia.handleEvent(BlockEvent(ctx,dummyBlock))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0))
            }
        })
    }
}
