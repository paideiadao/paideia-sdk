package im.paideia.common.contracts

import im.paideia.Paideia
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.filtering.FilterNode
import im.paideia.governance.contracts.ProposalContract
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.NetworkType

import scala.collection.mutable.HashMap
import scala.util.Success
import scala.util.Try
import scala.util.Failure

/**
  * Trait representing a Paideia Actor.
  */
trait PaideiaActor {

  /**
    * A HashMap containing contract instances. The key is the contract's serialised hash value
    */
  var contractInstances: HashMap[List[Byte], PaideiaContract] =
    HashMap[List[Byte], PaideiaContract]()

  /**
    * Clears contractInstances HashMap entry.
    */
  def clear = {
    contractInstances = HashMap[List[Byte], PaideiaContract]()
  }

  def apply(contractSignature: PaideiaContractSignature): PaideiaContract = ???

  /**
    * Gets an instance of provided T by creating or getting a cached instance of the contract from the contractInstances map.
    *
    * @param contractSignature The signature of the Paideia contract
    * @param default The default instance of the Paideia Contract.
    * @tparam T Generic type of Paideia Contract subtype
    * @return A new instance of provided subtype of Paideia Contract.
    */
  def getContractInstance[T <: PaideiaContract](
    contractSignature: PaideiaContractSignature,
    default: T
  ): T = {
    val contractInstance = contractInstances
      .getOrElse(default.contractSignature.contractHash, default)
      .asInstanceOf[T]
    contractInstances(contractInstance.contractSignature.contractHash) = contractInstance
    Paideia.instantiateActor(contractInstance.contractSignature)
    contractInstance
  }

  /**
    * Handles incoming PaideiaEvent by merging responses from all registered Paideia contracts using the handleEvent method.
    *
    * @param event Incoming Paideia Event to be passed on to registered contracts
    * @return An instance of PaideiaEventResponse that has been merged after being handled by registered contracts.
    */
  def handleEvent(event: PaideiaEvent): PaideiaEventResponse =
    PaideiaEventResponse.merge(
      contractInstances.values
        .map(pc => Try { pc.handleEvent(event) })
        .map(_ match {
          case Success(resp) => resp
          case Failure(exception) =>
            PaideiaEventResponse(-6, exceptions = List(exception))
        })
        .toList
    )

  /**
    * Generates a list of InputBoxes filtered by the given FilterNode.
    *
    * @param boxFilter The Ergo Filters object used to filter input boxes.
    * @return A List of Input Boxes.
    */
  def getBox(boxFilter: FilterNode): List[InputBox] =
    contractInstances.values.flatMap(_.getBox(boxFilter)).toList

  /**
    * Retrieves the proposal contract by serialised hash.
    *
    * @param contractHash The hash value of the ProposalContract.
    * @return Try with either the ProposalContract, or an exception in case target is not a proposal contract.
    */
  def getProposalContract(contractHash: List[Byte]): Try[ProposalContract] =
    Try {
      contractInstances(contractHash) match {
        case contract: ProposalContract => contract
        case _ =>
          throw new Exception("Not a proposal contract")
      }
    }
}
