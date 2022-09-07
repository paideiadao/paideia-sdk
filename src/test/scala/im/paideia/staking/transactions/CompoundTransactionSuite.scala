package im.paideia.staking.transactions

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.staking.StakingConfig
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.StakeStateBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.staking.StakeTransaction
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.InputBox
import im.paideia.staking.CompoundTransaction
import scala.util.Random
import im.paideia.governance.DAOConfig
import im.paideia.staking.StakingConfigBox
import im.paideia.util.Util
import im.paideia.common.PaideiaTestSuite

class CompoundTransactionSuite extends PaideiaTestSuite {

    test("Sign compound tx on 1 staker") {
        val stakingConfig = StakingConfig.test
        val daoConfig = DAOConfig.test
        val state = TotalStakingState(stakingConfig, 0L)
        val dummyKey = "07ef831684d35534989d62b97ce4da8a732dba9ca02836f8f6e00cbfdb5a0005"
        state.stake(dummyKey,100L)
        Range(0,stakingConfig.emissionDelay.toInt).foreach(_ => state.emit(9999999999999999L,9999999L))
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                val stakeStateInput = StakeStateBox(ctx,state,100000000L,daoConfig).inputBox(ctx)
                val stakingConfigInput = StakingConfigBox(ctx,stakingConfig,daoConfig).inputBox(ctx)
                val userInput = ctx.newTxBuilder().outBoxBuilder()
                    .contract(dummyAddress.toErgoContract())
                    .value(10000000000L)
                    .tokens(new ErgoToken(state.stakingConfig.stakedTokenId,10000000L))
                    .build().convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d",2)

                val compoundTransaction = CompoundTransaction(ctx,stakeStateInput,stakingConfigInput,userInput,1,state,dummyAddress.getErgoAddress(),daoConfig)
                val unsigned = compoundTransaction.unsigned()
                ctx.newProverBuilder().build().sign(compoundTransaction.unsigned())
            }
        })
    }
    
    test("Sign 2xcompound tx on 125 stakers out of 10000") {
        val stakingConfig = StakingConfig.test
        val daoConfig = DAOConfig.test
        val state = TotalStakingState(stakingConfig, 0L)
        Range(0,10000).foreach(_ => state.stake(Util.randomKey,100L))
        Range(0,stakingConfig.emissionDelay.toInt).foreach(_ => state.emit(9999999999999999L,999999999L))
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                val stakeStateInput = StakeStateBox(ctx,state,100000000L,daoConfig).inputBox(ctx)
                val stakingConfigInput = StakingConfigBox(ctx,stakingConfig,daoConfig).inputBox(ctx)
                val userInput = ctx.newTxBuilder().outBoxBuilder()
                    .contract(dummyAddress.toErgoContract())
                    .value(10000000000L)
                    .tokens(new ErgoToken(state.stakingConfig.stakedTokenId,10000000L))
                    .build().convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d",2)

                val compoundTransaction = CompoundTransaction(ctx,stakeStateInput,stakingConfigInput,userInput,750,state,dummyAddress.getErgoAddress(),daoConfig)

                val signed = ctx.newProverBuilder().build().sign(compoundTransaction.unsigned())

                val compoundTransaction2 = CompoundTransaction(ctx,signed.getOutputsToSpend().get(0),stakingConfigInput,signed.getOutputsToSpend().get(2),750,state,dummyAddress.getErgoAddress(),daoConfig)
                
                ctx.newProverBuilder().build().sign(compoundTransaction2.unsigned())
            }
        })
    }
}
