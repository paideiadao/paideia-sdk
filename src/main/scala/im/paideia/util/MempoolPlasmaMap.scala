package im.paideia.util

import work.lithos.plasma.collections.LocalPlasmaBase
import scorex.crypto.authds.avltree.batch.VersionedAVLStorage
import sigma.data.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.ByteConversion
import scorex.crypto.hash.Digest32
import scala.collection.mutable.HashMap
import scorex.crypto.authds.ADDigest
import work.lithos.plasma.collections.PlasmaMap
import work.lithos.plasma.collections.LocalPlasmaMap
import scorex.crypto.authds.avltree.batch.PersistentBatchAVLProver
import scala.collection.mutable.Queue
import scorex.crypto.hash.Blake2b256
import work.lithos.plasma.collections.Operations._
import work.lithos.plasma.collections.ProvenResult
import work.lithos.plasma.collections.OpResult
import scorex.crypto.authds.avltree.batch.Insert
import scorex.crypto.authds.avltree.batch.BatchAVLProver
import work.lithos.plasma.collections.Proof
import scorex.crypto.authds.avltree.batch.Update
import scorex.crypto.authds.avltree.batch.Remove
import scorex.crypto.authds.avltree.batch.Lookup
import sigma.data.AvlTreeData
import sigma.AvlTree
import org.ergoplatform.appkit.ErgoValue
import sigma.Colls

class MempoolPlasmaMap[K, V](
  store: VersionedAVLStorage[Digest32],
  override val flags: AvlTreeFlags,
  override val params: PlasmaParameters,
  mempoolMaps: HashMap[List[Byte], PlasmaMapWithMap[K, V]] =
    new HashMap[List[Byte], PlasmaMapWithMap[K, V]](),
  private var newlyConfirmedMap: Option[PlasmaMapWithMap[K, V]] = None,
  opQueue: Queue[(Int, BatchOperation[K, V])] = Queue.empty[(Int, BatchOperation[K, V])]
)(implicit val convertKey: ByteConversion[K], convertVal: ByteConversion[V])
  extends LocalPlasmaBase[K, V] {

  val localMap = new LocalPlasmaMap[K, V](store, flags, params)

  override val prover: PersistentBatchAVLProver[Digest32, Blake2b256.type] =
    localMap.prover

  override def digest: ADDigest =
    newlyConfirmedMap.map(_.prover.digest).getOrElse(prover.digest)

  def initiate(): PlasmaMapWithMap[K, V] = {
    newlyConfirmedMap = Some(PlasmaMapWithMap(localMap.toPlasmaMap))
    newlyConfirmedMap.get
  }

  def ergoAVLData(digestOpt: Option[ADDigest] = None): AvlTreeData =
    AvlTreeData(
      Colls.fromArray(digestOpt.getOrElse(digest)),
      flags,
      params.keySize,
      params.valueSizeOpt
    )

  def ergoAVLTree(digestOpt: Option[ADDigest] = None): AvlTree =
    sigmastate.eval.avlTreeDataToAvlTree(ergoAVLData(digestOpt))

  def ergoValue(digestOpt: Option[ADDigest] = None): ErgoValue[AvlTree] =
    ErgoValue.of(ergoAVLData(digestOpt))

  def insertWithDigest(
    keyVals: (K, V)*
  )(
    digestOrHeight: Either[ADDigest, Int],
    inPlace: Boolean = false
  ): ProvenResultWithDigest[V] = {
    val map = digestOrHeight match {
      case Right(i) => newlyConfirmedMap.getOrElse(initiate())
      case Left(onDigest) =>
        val sourceMap =
          if (onDigest.sameElements(digest))
            newlyConfirmedMap.getOrElse(initiate())
          else
            mempoolMaps(onDigest.toList)
        if (inPlace) {
          mempoolMaps.remove(sourceMap.digest.toList)
          sourceMap
        } else
          sourceMap.copy()

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
      case Right(i) =>
        opQueue.enqueue((i, InsertBatch(keyVals)))
        map.cachedMap = None
      case Left(onDigest) =>
        mempoolMaps(map.prover.digest.toList) = map
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
          PlasmaMapWithMap(newlyConfirmedMap.getOrElse(initiate()).copy())
        else
          PlasmaMapWithMap(mempoolMaps(onDigest.toList).copy())
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
      case Right(i) =>
        opQueue.enqueue((i, UpdateBatch(newKeyVals)))
        map.cachedMap = None
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
        getMap(Some(onDigest)).get.copy()
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
      case Right(i) =>
        opQueue.enqueue((i, DeleteBatch(keys)))
        map.cachedMap = None
      case Left(onDigest) => mempoolMaps(map.digest.toList) = map
    }
    ProvenResultWithDigest(response, Proof(proof), map.digest)
  }

  def lookUpWithDigest(
    keys: K*
  )(digestOpt: Option[ADDigest] = None): ProvenResult[V] = {
    val map = getMap(digestOpt).get
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

  def lookUpDeleteWithDigest(
    keys: K*
  )(digestOrHeight: Either[ADDigest, Int]): ProvenResultWithDigest[V] = {
    val map = digestOrHeight match {
      case Right(i) => newlyConfirmedMap.getOrElse(initiate())
      case Left(onDigest) =>
        getMap(Some(onDigest)).get.copy()
    }
    map.prover.generateProof()
    val response = keys
      .map(k =>
        OpResult(
          map.prover
            .performOneOperation(Lookup(convertKey.toADKey(k)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val removeResponse = keys
      .map(k =>
        OpResult(
          map.prover
            .performOneOperation(Remove(convertKey.toADKey(k)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val proof = map.prover.generateProof()
    digestOrHeight match {
      case Right(i) =>
        opQueue.enqueue((i, DeleteBatch(keys)))
        map.cachedMap = None
      case Left(onDigest) => mempoolMaps(map.digest.toList) = map
    }
    ProvenResultWithDigest(response ++ removeResponse, Proof(proof), map.digest)
  }

  def getMap(digestOpt: Option[ADDigest]): Option[PlasmaMapWithMap[K, V]] = {
    digestOpt match {
      case None => Some(newlyConfirmedMap.getOrElse(initiate()))
      case Some(onDigest) =>
        if (onDigest.sameElements(digest))
          Some(newlyConfirmedMap.getOrElse(initiate()))
        else
          mempoolMaps.get(onDigest.toList)
    }
  }

  def delete(keys: K*): ProvenResult[V] =
    deleteWithDigest(keys: _*)(Right(0)).toProvenResult

  def insert(keyVals: (K, V)*): ProvenResult[V] =
    insertWithDigest(keyVals: _*)(Right(0)).toProvenResult

  def lookUp(keys: K*): ProvenResult[V] =
    lookUpWithDigest(keys: _*)(None)
  def persistentItems: Seq[(K, V)] = ???

  override val storage: VersionedAVLStorage[Digest32] = store
  def toMap: Map[K, V]                                = ???

  def update(newKeyVals: (K, V)*): ProvenResult[V] =
    updateWithDigest(newKeyVals: _*)(Right(0)).toProvenResult

  def copy(newStore: VersionedAVLStorage[Digest32]): MempoolPlasmaMap[K, V] = {
    val newMempoolMaps = mempoolMaps.map(kv => (kv._1, kv._2.copy()))
    new MempoolPlasmaMap[K, V](
      newStore,
      flags,
      params,
      newMempoolMaps,
      newlyConfirmedMap.map(_.copy()),
      opQueue.clone()
    )
  }
}

object MempoolPlasmaMap {

  def apply[K, V](
    store: VersionedAVLStorage[Digest32],
    flags: AvlTreeFlags,
    params: PlasmaParameters
  )(implicit
    convertKey: ByteConversion[K],
    convertVal: ByteConversion[V]
  ): MempoolPlasmaMap[K, V] = {
    new MempoolPlasmaMap[K, V](store, flags, params)
  }
}
