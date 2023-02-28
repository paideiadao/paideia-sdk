package im.paideia.staking.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.common.PaideiaEventResponse
import im.paideia.common.TransactionEvent
import im.paideia.common.PaideiaEvent
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Address
import java.util.HashMap
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAOConfigKey
import org.ergoplatform.appkit.ErgoId
import java.nio.charset.StandardCharsets
import im.paideia.staking.boxes.AddStakeProxyBox
import im.paideia.Paideia
import im.paideia.staking.transactions.AddStakeTransaction

class AddStakeProxy(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, 
        stakeKey: String, addAmount: Long, userAddress: String): AddStakeProxyBox = {
        AddStakeProxyBox(ctx,this,Paideia.getConfig(contractSignature.daoKey),stakeKey,userAddress,addAmount)
    }

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        val response: PaideiaEventResponse = event match {
            case te: TransactionEvent => {
                PaideiaEventResponse.merge(te.tx.getOutputs().asScala.map{(eto: ErgoTransactionOutput) => {
                    val etotree = eto.getErgoTree()
                    val ergotree = ergoTree.bytesHex
                    if (eto.getErgoTree()==ergoTree.bytesHex) {
                        PaideiaEventResponse(1,List(AddStakeTransaction(
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

object AddStakeProxy extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): AddStakeProxy = 
        getContractInstance[AddStakeProxy](contractSignature,new AddStakeProxy(contractSignature))
}