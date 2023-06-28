package im.paideia.util

import scorex.crypto.authds.ADDigest
import work.lithos.plasma.collections.OpResult
import work.lithos.plasma.collections.Proof
import work.lithos.plasma.collections.ProvenResult

final case class ProvenResultWithDigest[V](
  response: Seq[OpResult[V]],
  proof: Proof,
  digest: ADDigest
) {
  def toProvenResult = ProvenResult(response, proof)
}
