package im.paideia.util

import io.getblok.getblok_plasma.collections.LocalPlasmaBase
import scorex.crypto.authds.avltree.batch.VersionedAVLStorage
import sigmastate.AvlTreeFlags
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.ByteConversion
import scorex.crypto.hash.Digest32
import scala.collection.mutable.HashMap
import scorex.crypto.authds.ADDigest
import io.getblok.getblok_plasma.collections.PlasmaMap
import io.getblok.getblok_plasma.collections.LocalPlasmaMap
import scorex.crypto.authds.avltree.batch.PersistentBatchAVLProver
import scala.collection.mutable.Queue
import scorex.crypto.hash.Blake2b256
import io.getblok.getblok_plasma.collections.Operations._
import io.getblok.getblok_plasma.collections.ProvenResult
import io.getblok.getblok_plasma.collections.OpResult
import scorex.crypto.authds.avltree.batch.Insert
import scorex.crypto.authds.avltree.batch.BatchAVLProver
import io.getblok.getblok_plasma.collections.Proof
import scorex.crypto.authds.avltree.batch.Update
import scorex.crypto.authds.avltree.batch.Remove
import scorex.crypto.authds.avltree.batch.Lookup
import sigmastate.AvlTreeData
import special.sigma.AvlTree
import org.ergoplatform.appkit.ErgoValue

class MempoolPlasmaMap[K, V](
  store: VersionedAVLStorage[Digest32],
  override val flags: AvlTreeFlags,
  override val params: PlasmaParameters
)(implicit val convertKey: ByteConversion[K], convertVal: ByteConversion[V])
  extends LocalPlasmaBase[K, V] {

  private var newlyConfirmedMap: Option[PlasmaMap[K, V]] = None

  private val mempoolMaps: HashMap[List[Byte], PlasmaMap[K, V]] =
    new HashMap[List[Byte], PlasmaMap[K, V]]()

  val localMap: LocalPlasmaMap[K, V] = new LocalPlasmaMap[K, V](store, flags, params)

  override val prover: PersistentBatchAVLProver[Digest32, Blake2b256.type] =
    localMap.prover

  override def digest: ADDigest =
    newlyConfirmedMap.map(_.prover.digest).getOrElse(prover.digest)

  private val opQueue: Queue[(Int, BatchOperation[K, V])] =
    Queue.empty[(Int, BatchOperation[K, V])]

  def initiate(): PlasmaMap[K, V] = {
    newlyConfirmedMap = Some(localMap.toPlasmaMap)
    newlyConfirmedMap.get
  }

  def ergoAVLData(digestOpt: Option[ADDigest] = None): AvlTreeData =
    AvlTreeData(digestOpt.getOrElse(digest), flags, params.keySize, params.valueSizeOpt)

  def ergoAVLTree(digestOpt: Option[ADDigest] = None): AvlTree =
    sigmastate.eval.avlTreeDataToAvlTree(ergoAVLData(digestOpt))

  def ergoValue(digestOpt: Option[ADDigest] = None): ErgoValue[AvlTree] =
    ErgoValue.of(ergoAVLData(digestOpt))

  def insertWithDigest(
    keyVals: (K, V)*
  )(digestOrHeight: Either[ADDigest, Int]): ProvenResultWithDigest[V] = {
    val map = digestOrHeight match {
      case Right(i) => newlyConfirmedMap.getOrElse(initiate())
      case Left(onDigest) =>
        if (onDigest.sameElements(digest))
          newlyConfirmedMap.getOrElse(initiate()).copy()
        else
          mempoolMaps(onDigest.toList).copy()
    }
    map.prover.generateProof()
    val response = keyVals
      .map(kv =>
        OpResult(
          map.prover
            .performOneOperation(
              Insert(convertKey.toADKey(kv._1), convertVal.toADVal(kv._2))
            )
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val proof = map.prover.generateProof()
    digestOrHeight match {
      case Right(i)       => opQueue.enqueue((i, InsertBatch(keyVals)))
      case Left(onDigest) => mempoolMaps(map.prover.digest.toList) = map
    }
    ProvenResultWithDigest(response, Proof(proof), map.digest)
  }

  def updateWithDigest(
    newKeyVals: (K, V)*
  )(digestOrHeight: Either[ADDigest, Int]): ProvenResultWithDigest[V] = {
    val map = digestOrHeight match {
      case Right(i) => newlyConfirmedMap.getOrElse(initiate())
      case Left(onDigest) =>
        if (onDigest.sameElements(digest))
          newlyConfirmedMap.getOrElse(initiate()).copy()
        else
          mempoolMaps(onDigest.toList).copy()
    }
    map.prover.generateProof()
    val response = newKeyVals
      .map(kv =>
        OpResult(
          map.prover
            .performOneOperation(
              Update(convertKey.toADKey(kv._1), convertVal.toADVal(kv._2))
            )
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val proof = map.prover.generateProof()
    digestOrHeight match {
      case Right(i)       => opQueue.enqueue((i, UpdateBatch(newKeyVals)))
      case Left(onDigest) => mempoolMaps(map.prover.digest.toList) = map
    }
    ProvenResultWithDigest(response, Proof(proof), map.digest)
  }

  def deleteWithDigest(
    keys: K*
  )(digestOrHeight: Either[ADDigest, Int]): ProvenResultWithDigest[V] = {
    val map = digestOrHeight match {
      case Right(i) => newlyConfirmedMap.getOrElse(initiate())
      case Left(onDigest) =>
        if (onDigest.sameElements(digest))
          newlyConfirmedMap.getOrElse(initiate()).copy()
        else
          mempoolMaps(onDigest.toList).copy()
    }
    map.prover.generateProof()
    val response = keys
      .map(k =>
        OpResult(
          map.prover
            .performOneOperation(Remove(convertKey.toADKey(k)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val proof = map.prover.generateProof()
    digestOrHeight match {
      case Right(i)       => opQueue.enqueue((i, DeleteBatch(keys)))
      case Left(onDigest) => mempoolMaps(map.prover.digest.toList) = map
    }
    ProvenResultWithDigest(response, Proof(proof), map.digest)
  }

  def lookUpWithDigest(
    keys: K*
  )(digestOpt: Option[ADDigest] = None): ProvenResult[V] = {
    val map = digestOpt match {
      case None => newlyConfirmedMap.getOrElse(initiate())
      case Some(onDigest) =>
        if (onDigest.sameElements(digest))
          newlyConfirmedMap.getOrElse(initiate())
        else
          mempoolMaps(onDigest.toList)
    }
    val response = keys
      .map(k =>
        OpResult(
          map.prover
            .performOneOperation(Lookup(convertKey.toADKey(k)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val proof = map.prover.generateProof()
    ProvenResult(response, Proof(proof))
  }

  def delete(keys: K*): io.getblok.getblok_plasma.collections.ProvenResult[V] =
    deleteWithDigest(keys: _*)(Right(0)).toProvenResult

  def insert(keyVals: (K, V)*): io.getblok.getblok_plasma.collections.ProvenResult[V] =
    insertWithDigest(keyVals: _*)(Right(0)).toProvenResult

  def lookUp(keys: K*): io.getblok.getblok_plasma.collections.ProvenResult[V] =
    lookUpWithDigest(keys: _*)(None)
  def persistentItems: Seq[(K, V)] = ???

  override val storage: VersionedAVLStorage[Digest32] = store
  def toMap: Map[K, V]                                = ???

  def update(newKeyVals: (K, V)*): io.getblok.getblok_plasma.collections.ProvenResult[V] =
    updateWithDigest(newKeyVals: _*)(Right(0)).toProvenResult
}

object MempoolPlasmaMap {

  def apply[K, V](
    store: VersionedAVLStorage[Digest32],
    flags: AvlTreeFlags,
    params: PlasmaParameters
  )(
    implicit convertKey: ByteConversion[K],
    convertVal: ByteConversion[V]
  ): MempoolPlasmaMap[K, V] = {
    new MempoolPlasmaMap[K, V](store, flags, params)
  }
}
