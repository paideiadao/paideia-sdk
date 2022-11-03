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
import im.paideia.staking.boxes._
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfig
import im.paideia.staking.transactions._
import im.paideia.common.PaideiaTestSuite
import sigmastate.lang.exceptions.InterpreterException
import im.paideia.util.Util
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.util.ConfKeys
import im.paideia.common.contracts.Config
import im.paideia.staking.contracts.StakeProxy
import org.ergoplatform.restapi.client.ErgoTransaction
import im.paideia.Paideia
import im.paideia.common.TransactionEvent

class StakeTransactionSuite extends PaideiaTestSuite {
    test("Sign stake tx on empty state") {
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                PaideiaTestSuite.init(ctx)
                val dao = StakingTest.testDAO
                val state = TotalStakingState(dao.key)

                val dummyAddress = Address.create("4MQyML64GnzMxZgm")

                val stakingContract = PlasmaStaking(PaideiaContractSignature(daoKey = dao.key))
                dao.config.set(ConfKeys.im_paideia_contracts_staking,stakingContract.contractSignature)

                val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
                
                val configBox = Config(configContract.contractSignature).box(ctx,dao).inputBox()
                configContract.clearBoxes()
                configContract.newBox(configBox,false)

                
                val stakingStateBox = PlasmaStaking(stakingContract.contractSignature).box(
                    ctx,
                    dao.config,
                    state,
                    100000000L
                ).inputBox()
                stakingContract.clearBoxes()
                stakingContract.newBox(stakingStateBox,false)

                val stakeProxyContract = StakeProxy(PaideiaContractSignature(daoKey=dao.key))
                val stakeProxyBox = stakeProxyContract.box(ctx,dummyAddress.toString(),1000L).ergoTransactionOutput()
                val dummyTx = (new ErgoTransaction()).addOutputsItem(stakeProxyBox)
                val eventResponse = Paideia.handleEvent(TransactionEvent(ctx,false,dummyTx))
                assert(eventResponse.unsignedTransactions.size===1)
                ctx.newProverBuilder().build().sign(eventResponse.unsignedTransactions(0))
            }
        })
    }

    test("Fail stake tx on empty state with stake key address switched out") {
        // val stakingConfig = StakingConfig.test
        // val daoConfig = DAOConfig.test
        // val state = TotalStakingState(stakingConfig, 0L)
        // val dummyAddress = Address.create("4MQyML64GnzMxZgm")
        // val thiefAddress = Address.create("9hyDXH72HoNTiG2pvxFQwxAhWBU8CrbvwtJDtnYoa4jfpaSk1d3")
        // val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        // ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
        //     override def apply(_ctx: BlockchainContext): Unit = {
        //         val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        //         val stakeStateInput = StakeStateBox(ctx,state,100000000L,daoConfig).inputBox()
        //         val stakingConfigInput = StakingConfigBox(ctx,stakingConfig,daoConfig).inputBox()
        //         val proxyInput = ProxyStakeBox(ctx,stakingConfig,daoConfig,stakeAmount=1000L,stakeKeyTarget=dummyAddress).inputBox()
        //         val stakeTransaction = StakeTransaction(ctx,stakeStateInput,stakingConfigInput,proxyInput,1000L,state,dummyAddress.getErgoAddress(),daoConfig)
        //         val unsignedTx = stakeTransaction.unsigned()
        //         val correctOutput = unsignedTx.getOutputs().get(1)
        //         val falseOutput = ctx.newTxBuilder().outBoxBuilder()
        //             .contract(thiefAddress.toErgoContract())
        //             .creationHeight(correctOutput.getCreationHeight())
        //             .registers(correctOutput.getRegisters().asScala: _*)
        //             .tokens(correctOutput.getTokens().asScala: _*)
        //             .value(correctOutput.getValue()).build()
        //         val falseUnsignedTx = ctx.newTxBuilder()
        //             .boxesToSpend(unsignedTx.getInputs())
        //             .fee(stakeTransaction.fee)
        //             .outputs(unsignedTx.getOutputs().get(0),falseOutput)
        //             .sendChangeTo(stakeTransaction.changeAddress).build()
        //         val thrown = intercept[InterpreterException] {
        //             ctx.newProverBuilder().build().sign(falseUnsignedTx)
        //         }
        //         assert(thrown.getMessage === "Script reduced to false")
        //     }
        // })
    }

    test("Fail stake tx on empty state with nftId switched out") {
        // val stakingConfig = StakingConfig.test
        // val falseStakingConfig = StakingConfig(
        //     Util.randomKey,
        //     stakingConfig.stakedTokenId,
        //     stakingConfig.emissionAmount,
        //     stakingConfig.emissionDelay,
        //     stakingConfig.cycleLength,
        //     stakingConfig.profitTokens)
        // val daoConfig = DAOConfig.test
        // val state = TotalStakingState(falseStakingConfig, 0L)
        // val dummyAddress = Address.create("4MQyML64GnzMxZgm")
        // val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        // ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
        //     override def apply(_ctx: BlockchainContext): Unit = {
        //         val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        //         val stakeStateInput = StakeStateBox(ctx,state,100000000L,daoConfig).inputBox()
        //         val stakingConfigInput = StakingConfigBox(ctx,falseStakingConfig,daoConfig).inputBox()
        //         val proxyInput = ProxyStakeBox(ctx,stakingConfig,daoConfig,stakeAmount=1000L,stakeKeyTarget=dummyAddress).inputBox()
        //         val stakeTransaction = StakeTransaction(ctx,stakeStateInput,stakingConfigInput,proxyInput,1000L,state,dummyAddress.getErgoAddress(),daoConfig)
        //         val thrown = intercept[InterpreterException] {
        //             ctx.newProverBuilder().build().sign(stakeTransaction.unsigned())
        //         }
        //         assert(thrown.getMessage === "Script reduced to false")
        //     }
        // })
    }
}
