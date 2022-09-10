package im.paideia.staking.transactions

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.staking.StakingConfig
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
import im.paideia.governance.DAOConfig
import im.paideia.staking.boxes._
import im.paideia.common.PaideiaTestSuite

class EmitTransactionSuite extends PaideiaTestSuite {
    test("Sign emit tx on empty state") {
        val stakingConfig = StakingConfig.test
        val daoConfig = DAOConfig.test
        val state = TotalStakingState(stakingConfig, 0L)
        val dummyAddress = Address.create("4MQyML64GnzMxZgm")
        val ergoClient = createMockedErgoClient(MockData(Nil,Nil))
        ergoClient.execute(new java.util.function.Function[BlockchainContext,Unit] {
            override def apply(_ctx: BlockchainContext): Unit = {
                val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
                val stakeStateInput = StakeStateBox(ctx,state,100000000L,daoConfig).inputBox()
                val stakingConfigInput = StakingConfigBox(ctx,stakingConfig,daoConfig).inputBox()
                val userInput = ctx.newTxBuilder().outBoxBuilder()
                    .contract(dummyAddress.toErgoContract())
                    .value(10000000000L)
                    .tokens(new ErgoToken(state.stakingConfig.stakedTokenId,10000000L))
                    .build().convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d",2)

                val emitTransaction = EmitTransaction(ctx,stakeStateInput,stakingConfigInput,userInput,state,dummyAddress.getErgoAddress(),daoConfig)
                ctx.newProverBuilder().build().sign(emitTransaction.unsigned())
            }
        })
    }
}
