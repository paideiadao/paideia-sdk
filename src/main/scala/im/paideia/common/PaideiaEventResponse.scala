package im.paideia.common

import org.ergoplatform.appkit.UnsignedTransaction
import im.paideia.common.transactions.PaideiaTransaction

final case class PaideiaEventResponse(
    status: Int,
    unsignedTransactions: List[PaideiaTransaction] = List[PaideiaTransaction]()
) {
  def +(that: PaideiaEventResponse): PaideiaEventResponse = {
    PaideiaEventResponse(this.status.max(that.status),this.unsignedTransactions++that.unsignedTransactions)
  }
}

object PaideiaEventResponse {
  implicit def ordering[A <: PaideiaEventResponse]: Ordering[A] = Ordering.by(_.status)

  def merge(responses: List[PaideiaEventResponse]): PaideiaEventResponse = {
    responses.foldLeft(PaideiaEventResponse(0))(_ + _)
  }
}
