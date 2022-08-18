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

class CompoundTransactionSuite extends AnyFunSuite {
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
}
