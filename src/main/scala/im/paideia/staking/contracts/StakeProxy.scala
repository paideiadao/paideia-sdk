package im.paideia.staking.contracts

import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.Paideia
import org.ergoplatform.appkit.Address
import im.paideia.staking.boxes.StakeProxyBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.staking.transactions.StakeTransaction
import im.paideia.common.PaideiaEventResponse
import im.paideia.common.PaideiaEvent
import im.paideia.common.TransactionEvent
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.util.Env

class StakeProxy(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, userAddress: String, stakeAmount: Long): StakeProxyBox = {
        StakeProxyBox(ctx,this,Paideia.getConfig(contractSignature.daoKey),userAddress,stakeAmount)
    }

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        val response: PaideiaEventResponse = event match {
            case te: TransactionEvent => {
                PaideiaEventResponse.merge(te.tx.getOutputs().asScala.map{(eto: ErgoTransactionOutput) => {
                    val etotree = eto.getErgoTree()
                    val ergotree = ergoTree.bytesHex
                    if (eto.getErgoTree()==ergoTree.bytesHex) {
                        PaideiaEventResponse(1,List(StakeTransaction(
                            te.ctx,
                            new InputBoxImpl(eto),
                            Address.create(Env.operatorAddress).getErgoAddress,
                            contractSignature.daoKey)))
                    } else {
                        PaideiaEventResponse(0)
                    }
                }}.toList)
            }
            case _ => PaideiaEventResponse(0)
        }
        val superResponse = super.handleEvent(event)
        response
    }
}

object StakeProxy extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): StakeProxy = getContractInstance[StakeProxy](contractSignature,new StakeProxy(contractSignature))
}