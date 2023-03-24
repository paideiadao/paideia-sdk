package im.paideia.common.events

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.UnsignedTransaction

/**
  * Represents the response of a PaideiaEvent.
  *
  * @param status The numeric status.
  * @param unsignedTransactions Optional list of PaideiaTransactions.
  */
final case class PaideiaEventResponse(
  status: Int,
  unsignedTransactions: List[PaideiaTransaction] = List[PaideiaTransaction]()
) {

  /**
    * Merges two PaideiaEventResponses by combining their transaction lists.
    *
    * @param that Another PaideiaEventResponse to be merged with this one.
    * @return A new PaideiaEventResponse with combined unsignedTransactions and the maximum status code.
    */
  def +(that: PaideiaEventResponse): PaideiaEventResponse = {
    PaideiaEventResponse(
      this.status.max(that.status),
      this.unsignedTransactions ++ that.unsignedTransactions
    )
  }
}

object PaideiaEventResponse {

  /**
    * Implicit ordering method for PaideiaEventResponse.
    *
    * @tparam A Type param that must extend PaideiaEventResponse.
    * @return Ordering[A] that orders responses by status.
    */
  implicit def ordering[A <: PaideiaEventResponse]: Ordering[A] = Ordering.by(_.status)

  /**
    * Merges a list of PaideiaEventResponses by folding and concatenating all unsignedTransactions.
    *
    * @param responses A list of PaideiaEventResponses to be merged.
    * @return A new PaideiaEventResponse with all transactions combined and the highest status code.
    */
  def merge(responses: List[PaideiaEventResponse]): PaideiaEventResponse = {
    responses.foldLeft(PaideiaEventResponse(0))(_ + _)
  }
}
