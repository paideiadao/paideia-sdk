package im.paideia.common.contracts

import org.ergoplatform.appkit.NetworkType
import im.paideia.common.PaideiaEvent
import im.paideia.common.PaideiaEventResponse
import scala.collection.mutable.HashMap
import im.paideia.common.filtering.FilterNode
import org.ergoplatform.appkit.InputBox

trait PaideiaActor {
    val contractInstances: HashMap[PaideiaContractSignature,PaideiaContract] = HashMap[PaideiaContractSignature,PaideiaContract]()
    
    def apply(contractSignature: PaideiaContractSignature): PaideiaContract = ???

    def getContractInstance[T <: PaideiaContract](contractSignature: PaideiaContractSignature, default: T): T = {
        val contractInstance = contractInstances.getOrElse(default.contractSignature,default).asInstanceOf[T]
        contractInstances(default.contractSignature) = contractInstance
        contractInstance
    }

    def handleEvent(event: PaideiaEvent) : PaideiaEventResponse = PaideiaEventResponse.merge(contractInstances.values.map(_.handleEvent(event)).toList)

    def getBox(boxFilter: FilterNode): List[InputBox] = contractInstances.values.flatMap(_.getBox(boxFilter)).toList
}
