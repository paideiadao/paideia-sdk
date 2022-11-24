package im.paideia.common.contracts

import org.ergoplatform.appkit.NetworkType
import im.paideia.common.PaideiaEvent
import im.paideia.common.PaideiaEventResponse
import scala.collection.mutable.HashMap
import im.paideia.common.filtering.FilterNode
import org.ergoplatform.appkit.InputBox
import im.paideia.Paideia
import im.paideia.governance.contracts.ProposalContract
import scala.util.Success
import scala.util.Try

trait PaideiaActor {
    val contractInstances: HashMap[List[Byte],PaideiaContract] = HashMap[List[Byte],PaideiaContract]()
    
    def apply(contractSignature: PaideiaContractSignature): PaideiaContract = ???

    def getContractInstance[T <: PaideiaContract](contractSignature: PaideiaContractSignature, default: T): T = {
        val contractInstance = contractInstances.getOrElse(default.contractSignature.contractHash,default).asInstanceOf[T]
        contractInstances(contractInstance.contractSignature.contractHash) = contractInstance
        Paideia.instantiateActor(contractInstance.contractSignature)
        contractInstance
    }

    def handleEvent(event: PaideiaEvent) : PaideiaEventResponse = PaideiaEventResponse.merge(contractInstances.values.map(_.handleEvent(event)).toList)

    def getBox(boxFilter: FilterNode): List[InputBox] = contractInstances.values.flatMap(_.getBox(boxFilter)).toList

    def getProposalContract(contractHash: List[Byte]): Try[ProposalContract] = 
        Try {
            contractInstances(contractHash) match {
                case contract: ProposalContract => contract
                case _ => throw new Exception("Not a proposal contract")
            }
        }
}
