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
import im.paideia.staking.boxes._
import scala.util.Random
import im.paideia.DAOConfig
import im.paideia.util.Util
import im.paideia.common.PaideiaTestSuite
import im.paideia.util.ConfKeys
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Config
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.Paideia
import im.paideia.common.TransactionEvent

class CompoundTransactionSuite extends PaideiaTestSuite {

    test("Sign compound tx on 1 staker") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val dao = StakingTest.testDAO
                val state = TotalStakingState(dao.key,0L)
                state.stake(Util.randomKey,100L)
                Range(0,dao.config[Long](ConfKeys.im_paideia_staking_emission_delay).toInt).foreach(_ => state.emit(9999999999999999L,9999999L))
                val dummyAddress = Address.create("4MQyML64GnzMxZgm")

                val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = dao.key))
                dao.config.set(ConfKeys.im_paideia_contracts_staking,stakingContract.contractSignature)

                val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
                
                val configBox = Config(configContract.contractSignature).box(ctx,dao).inputBox()
                configContract.clearBoxes()
                configContract.newBox(configBox,false)
                
                val stakingStateBox = stakingContract.box(
                    ctx,
                    dao.key,
                    100000000L
                ).ergoTransactionOutput()
                stakingContract.clearBoxes()

                val dummyTx = (new ErgoTransaction()).addOutputsItem(stakingStateBox)
                val eventResponse = Paideia.handleEvent(TransactionEvent(ctx,false,dummyTx))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0).unsigned)
            }
        })
    }
    
    test("Sign compound tx on 1000 stakers out of 10000") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val dao = StakingTest.testDAO
                val state = TotalStakingState(dao.key,0L)
                Range(0,10000).foreach(_ => state.stake(Util.randomKey,100L))
                Range(0,dao.config[Long](ConfKeys.im_paideia_staking_emission_delay).toInt).foreach(_ => state.emit(9999999999999999L,9999999L))
                val dummyAddress = Address.create("4MQyML64GnzMxZgm")

                val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = dao.key))
                dao.config.set(ConfKeys.im_paideia_contracts_staking,stakingContract.contractSignature)

                val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
                
                val configBox = Config(configContract.contractSignature).box(ctx,dao).inputBox()
                configContract.clearBoxes()
                configContract.newBox(configBox,false)

                
                val stakingStateBox = stakingContract.box(
                    ctx,
                    dao.key,
                    100000000L
                ).ergoTransactionOutput()
                stakingContract.clearBoxes()

                val dummyTx = (new ErgoTransaction()).addOutputsItem(stakingStateBox)
                val eventResponse = Paideia.handleEvent(TransactionEvent(ctx,false,dummyTx))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0).unsigned)
            }
        })
        // val stakingConfig = StakingConfig.test
        // val daoConfig = DAOConfig.test
        // val state = TotalStakingState(stakingConfig, 0L)
        // Range(0,10000).foreach(_ => state.stake(Util.randomKey,100L))
        // Range(0,stakingConfig.emissionDelay.toInt).foreach(_ => state.emit(9999999999999999L,999999999L))
        // val dummyAddress = Address.create("4MQyML64GnzMxZgm")
        // val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        // ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
        //     override def apply(_ctx: BlockchainContext): Unit = {
        //         val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        //         val stakeStateInput = StakeStateBox(ctx,state,100000000L,daoConfig).inputBox()
        //         val stakingConfigInput = StakingConfigBox(ctx,stakingConfig,daoConfig).inputBox()
        //         val userInput = ctx.newTxBuilder().outBoxBuilder()
        //             .contract(dummyAddress.toErgoContract())
        //             .value(10000000000L)
        //             .tokens(new ErgoToken(state.stakingConfig.stakedTokenId,10000000L))
        //             .build().convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d",2)

        //         val compoundTransaction = CompoundTransaction(ctx,stakeStateInput,stakingConfigInput,userInput,750,state,dummyAddress.getErgoAddress(),daoConfig)

        //         val signed = ctx.newProverBuilder().build().sign(compoundTransaction.unsigned())

        //         val compoundTransaction2 = CompoundTransaction(ctx,signed.getOutputsToSpend().get(0),stakingConfigInput,signed.getOutputsToSpend().get(2),750,state,dummyAddress.getErgoAddress(),daoConfig)
                
        //         ctx.newProverBuilder().build().sign(compoundTransaction2.unsigned())
        //     }
        // })
    }
}
