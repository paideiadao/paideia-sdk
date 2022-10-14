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

class StakeTransactionSuite extends PaideiaTestSuite {
    test("Sign stake tx on empty state") {
        // val stakingConfig = StakingConfig.test
        // val daoConfig = DAOConfig.test
        // val state = TotalStakingState(stakingConfig, 0L)
        // val dummyAddress = Address.create("4MQyML64GnzMxZgm")
        // val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        // ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
        //     override def apply(_ctx: BlockchainContext): Unit = {
        //         val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        //         val stakeStateInput = StakeStateBox(ctx,state,100000000L,daoConfig).inputBox()
        //         val stakingConfigInput = StakingConfigBox(ctx,stakingConfig,daoConfig).inputBox()
        //         val proxyInput = ProxyStakeBox(ctx,stakingConfig,daoConfig,stakeAmount=1000L,stakeKeyTarget=dummyAddress).inputBox()
        //         val stakeTransaction = StakeTransaction(ctx,stakeStateInput,stakingConfigInput,proxyInput,1000L,state,dummyAddress.getErgoAddress(),daoConfig)
        //         ctx.newProverBuilder().build().sign(stakeTransaction.unsigned())
        //     }
        // })
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
