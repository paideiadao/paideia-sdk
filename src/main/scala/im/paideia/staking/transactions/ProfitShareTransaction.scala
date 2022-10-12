package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.impl.ErgoTreeContract
import im.paideia.DAOConfig
import special.collection.Coll
import org.ergoplatform.appkit.ErgoToken
import scala.collection.JavaConverters._
import im.paideia.staking._
import im.paideia.staking.boxes._
import im.paideia.staking.contracts.PlasmaStaking

class ProfitShareTransaction extends PaideiaTransaction {
  
}

object ProfitShareTransaction {
    def apply(
        ctx: BlockchainContextImpl, 
        stakeStateInput: InputBox, 
        stakingConfigInput: InputBox,
        userInput: InputBox,
        profitErgToShare: Long,
        profitTokensToShare: List[ErgoToken], 
        state: TotalStakingState, 
        changeAddress: ErgoAddress,
        daoConfig: DAOConfig,
        stakingContract: PlasmaStaking): ProfitShareTransaction = 
    {
        if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
        val whiteListedTokens = stakingConfigInput.getRegisters().get(2).getValue.asInstanceOf[Coll[(Coll[Byte],Long)]]
                                .toArray
                                .map((c: (Coll[Byte],Long)) =>
                                    new ErgoId(c._1.toArray)   
                                )

        val profitToShare = Array.fill(whiteListedTokens.size+2)(0L)
        var extraStaked = 0L

        profitToShare(1) += profitErgToShare

        profitTokensToShare.foreach((token: ErgoToken) =>
            if (token.getId()==stakeStateInput.getTokens().get(1).getId()) {
                profitToShare(0) += token.getValue()
                extraStaked = token.getValue()
            } else {
                val index = whiteListedTokens.indexOf(token.getId())
                if (index >= 0) {
                    profitToShare(index+2) += token.getValue()
                } else {
                    throw new Exception("Token not supported for profite sharing")
                }
            }
        )

        val extraTokens = stakeStateInput.getTokens().subList(2,stakeStateInput.getTokens().size).asScala.map((token: ErgoToken) =>
            profitTokensToShare.find(_.getId()==token.getId()) match {
                case None => token
                case Some(value) => new ErgoToken(token.getId(),token.getValue()+value.getValue())
            }  
        )

        val newExtraTokens = profitTokensToShare.filter((token: ErgoToken) =>
            stakeStateInput.getTokens().asScala.find((t: ErgoToken) => t.getId()==token.getId()) match {
                case None => true
                case Some(value) => false
            }  
        )

        val contextVars = state.profitShare(profitToShare.toList)

        val stakeStateOutput = stakingContract.box(
            ctx,
            daoConfig,
            state,
            stakeStateInput.getTokens().get(1).getValue()+extraStaked,
            stakeStateInput.getValue()+profitErgToShare,
            extraTokens.toList ++ newExtraTokens)
      
        val reg = stakeStateOutput.registers
        val res = new ProfitShareTransaction()
        res.ctx = ctx
        res.changeAddress = changeAddress
        res.fee = 1000000
        res.inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),userInput)
        res.dataInputs = List[InputBox](stakingConfigInput)
        res.outputs = List[OutBox](stakeStateOutput.outBox)
        res
    }
}