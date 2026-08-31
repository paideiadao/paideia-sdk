package im.paideia.util

import sigma.data.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import scorex.crypto.authds.avltree.batch.BatchAVLProver
import work.lithos.plasma.ByteConversion
import scorex.crypto.hash.Digest32
import scorex.crypto.hash.Blake2b256
import work.lithos.plasma.collections.PlasmaMap
import scala.collection.mutable.HashMap
import scala.collection.mutable
import scorex.crypto.authds.avltree.batch.InternalProverNode
import scorex.crypto.authds.ADValue
import scorex.crypto.authds.avltree.batch.ProverLeaf
import scorex.crypto.authds.avltree.batch.serialization.BatchAVLProverSerializer
import org.ergoplatform.settings.ErgoAlgos.HF
import scala.util.Try
import scorex.crypto.authds.avltree.batch.serialization.BatchAVLProverManifest
import scorex.crypto.authds.avltree.batch.ProverNodes
import scorex.utils.Logger

class PlasmaMapWithMap[K, V](
  override val flags: AvlTreeFlags,
  override val params: PlasmaParameters,
  initProver: Option[BatchAVLProver[Digest32, Blake2b256.type]] = None
)(implicit override val convertKey: ByteConversion[K], convertVal: ByteConversion[V])
  extends PlasmaMap[K, V](flags, params, initProver) {

  /** Cache of the last `toMap` result. Self-invalidating: `toMap` only ever serves this
    * when `cachedDigest` still matches the tree's current digest, so a stale read is
    * impossible by construction - a cache miss (digest changed, or this is unset) always
    * rebuilds from the manifest and re-populates both fields.
    *
    * External `= None` assignments (MempoolPlasmaMap does this after mutating a
    * confirmed-height map) are legacy no-ops kept for source/binary compatibility with
    * paideia-state: unsetting the cache just forces the next `toMap` to rebuild, which
    * the digest check already guarantees whenever the tree actually changed.
    */
  var cachedMap: Option[Map[K, V]] = None

  private var cachedDigest: Option[List[Byte]] = None

  def toMap: Map[K, V] = {
    implicit val logger: Logger = Logger.Default
    val d                       = digest.toList
    if (cachedMap.isDefined && cachedDigest.contains(d)) {
      cachedMap.get
    } else {
      implicit val hf: HF = Blake2b256
      val plamaSerializer = new BatchAVLProverSerializer[Digest32, Blake2b256.type]

      val treeManifest: Try[BatchAVLProverManifest[Digest32]] =
        plamaSerializer.manifestFromBytes(getManifest().bytes, params.keySize)

      def collectLeafs(
        node: ProverNodes[Digest32],
        leafs: mutable.Buffer[(K, V)]
      ): mutable.Buffer[(K, V)] =
        node match {
          case i: InternalProverNode[Digest32] =>
            collectLeafs(i.left, leafs) ++ collectLeafs(i.right, leafs)
          case l: ProverLeaf[Digest32] =>
            if (l.value.size > 0)
              mutable.ArrayBuffer[(K, V)](
                (
                  convertKey.convertFromBytes(l.key),
                  convertVal.convertFromBytes(l.value)
                )
              )
            else
              leafs
        }
      val result =
        collectLeafs(treeManifest.get.root, mutable.ArrayBuffer[(K, V)]())
          .toMap[K, V]
      cachedMap    = Some(result)
      cachedDigest = Some(d)
      result
    }
  }

  override def copy(
    optFlags: Option[AvlTreeFlags],
    optParams: Option[PlasmaParameters]
  ): PlasmaMapWithMap[K, V] = PlasmaMapWithMap(super.copy(optFlags, optParams))

}

object PlasmaMapWithMap {

  def apply[K, V](pMap: PlasmaMap[K, V])(implicit
    convertKey: ByteConversion[K],
    convertVal: ByteConversion[V]
  ): PlasmaMapWithMap[K, V] =
    new PlasmaMapWithMap[K, V](pMap.flags, pMap.params, Some(pMap.prover))
}
