package im.paideia.util

import scorex.crypto.authds.ADDigest
import io.getblok.getblok_plasma.collections.OpResult
import io.getblok.getblok_plasma.collections.Proof
import io.getblok.getblok_plasma.collections.ProvenResult

final case class ProvenResultWithDigest[V](
  response: Seq[OpResult[V]],
  proof: Proof,
  digest: ADDigest
) {
  def toProvenResult = ProvenResult(response, proof)
}
