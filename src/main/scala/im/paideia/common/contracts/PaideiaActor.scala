package im.paideia.common.contracts

import org.ergoplatform.appkit.NetworkType
import im.paideia.common.PaideiaEvent
import im.paideia.common.PaideiaEventResponse
import scala.collection.mutable.HashMap

trait PaideiaActor {
    val contractInstances: HashMap[PaideiaContractSignature,PaideiaContract] = HashMap[PaideiaContractSignature,PaideiaContract]()
    
    def apply(contractSignature: PaideiaContractSignature): PaideiaContract = ???

    def getContractInstance[T <: PaideiaContract](contractSignature: PaideiaContractSignature): T = {
        val contractInstance = contractInstances.getOrElse(contractSignature,new PaideiaContract(contractSignature)).asInstanceOf[T]
        contractInstances(contractSignature) = contractInstance
        contractInstance
    }

    def handleEvent(event: PaideiaEvent) : PaideiaEventResponse = PaideiaEventResponse(0)
}
