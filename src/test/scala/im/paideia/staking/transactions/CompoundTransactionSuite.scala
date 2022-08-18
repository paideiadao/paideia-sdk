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

class CompoundTransactionSuite extends AnyFunSuite {

    def bytes2hex(bytes: Array[Byte], sep: Option[String] = None): String = {
        sep match {
            case None => bytes.map("%02x".format(_)).mkString
            case _ => bytes.map("%02x".format(_)).mkString(sep.get)
        }
        // bytes.foreach(println)
    }

    test("Sign compound tx on 1 staker") {
        val config = StakingConfig.test
        val state = TotalStakingState(config, 0L)
        val dummyKey = "07ef831684d35534989d62b97ce4da8a732dba9ca02836f8f6e00cbfdb5a0005"
        state.stake(dummyKey,100L)
        Range(0,config.emissionDelay.toInt).foreach(_ => state.emit(9999999999999999L))
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")
        val ergoClient = RestApiErgoClient.create("http://ergolui.com:9053",NetworkType.MAINNET,"","https://api.ergoplatform.com")
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                val stakeStateInput = StakeStateBox(ctx,state,100000000L).inputBox(ctx)
                val userInput = ctx.newTxBuilder().outBoxBuilder()
                    .contract(dummyAddress.toErgoContract())
                    .value(10000000000L)
                    .tokens(new ErgoToken(state.stakingConfig.stakedTokenId,10000000L))
                    .build().convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d",2)

                val compoundTransaction = CompoundTransaction(ctx,stakeStateInput,userInput,1,state,dummyAddress.getErgoAddress())
                ctx.newProverBuilder().build().sign(compoundTransaction.unsigned())
            }
        })
    }
    
    test("Sign 2xcompound tx on 125 stakers out of 10000") {
        val config = StakingConfig.test
        val state = TotalStakingState(config, 0L)
        Range(0,10000).foreach(_ => {
            val testKey = new Array[Byte](32)
            Random.nextBytes(testKey)
            state.stake(bytes2hex(testKey),100L)
        })
        Range(0,config.emissionDelay.toInt).foreach(_ => state.emit(9999999999999999L))
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")
        val ergoClient = RestApiErgoClient.create("http://ergolui.com:9053",NetworkType.MAINNET,"","https://api.ergoplatform.com")
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                val stakeStateInput = StakeStateBox(ctx,state,100000000L).inputBox(ctx)
                val userInput = ctx.newTxBuilder().outBoxBuilder()
                    .contract(dummyAddress.toErgoContract())
                    .value(10000000000L)
                    .tokens(new ErgoToken(state.stakingConfig.stakedTokenId,10000000L))
                    .build().convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d",2)

                val compoundTransaction = CompoundTransaction(ctx,stakeStateInput,userInput,125,state,dummyAddress.getErgoAddress())

                val signed = ctx.newProverBuilder().build().sign(compoundTransaction.unsigned())
                
                val compoundTransaction2 = CompoundTransaction(ctx,signed.getOutputsToSpend().get(0),signed.getOutputsToSpend().get(2),125,state,dummyAddress.getErgoAddress())
                
                ctx.newProverBuilder().build().sign(compoundTransaction2.unsigned())
            }
        })
    }
}
