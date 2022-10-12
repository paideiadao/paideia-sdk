package im.paideia.common

final case class PaideiaEventResponse(
    status: Int
)

object PaideiaEventResponse {
  implicit def ordering[A <: PaideiaEventResponse]: Ordering[A] = Ordering.by(_.status)
}
