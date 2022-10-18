package im.paideia.common

import org.ergoplatform.appkit.UnsignedTransaction

final case class PaideiaEventResponse(
    status: Int,
    unsignedTransactions: List[UnsignedTransaction] = List[UnsignedTransaction]()
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
