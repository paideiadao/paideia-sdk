package im.paideia.util

import work.lithos.plasma.collections.LocalPlasmaBase
import scorex.crypto.authds.avltree.batch.VersionedAVLStorage
import sigma.data.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.ByteConversion
import scorex.crypto.hash.Digest32
import scorex.crypto.authds.ADDigest
import work.lithos.plasma.collections.PlasmaMap
import work.lithos.plasma.collections.LocalPlasmaMap
import scorex.crypto.authds.avltree.batch.PersistentBatchAVLProver
import scala.collection.mutable.LinkedHashMap
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
import scorex.crypto.authds.avltree.batch.ProverNodes
import scorex.crypto.authds.avltree.batch.InternalProverNode
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.db.LDBVersionedStore
import sigma.data.AvlTreeData
import sigma.AvlTree
import org.ergoplatform.appkit.ErgoValue
import sigma.Colls
import im.paideia.Paideia

class MempoolPlasmaMap[K, V](
  store: VersionedAVLStorage[Digest32],
  override val flags: AvlTreeFlags,
  override val params: PlasmaParameters,
  mempoolMaps: LinkedHashMap[List[Byte], PlasmaMapWithMap[K, V]] =
    new LinkedHashMap[List[Byte], PlasmaMapWithMap[K, V]](),
  private var newlyConfirmedMap: Option[PlasmaMapWithMap[K, V]] = None,
  opQueue: Queue[(Int, BatchOperation[K, V])] = Queue.empty[(Int, BatchOperation[K, V])],
  val maxMempoolMaps: Int = 256
)(implicit val convertKey: ByteConversion[K], convertVal: ByteConversion[V])
  extends LocalPlasmaBase[K, V] {

  val localMap = new LocalPlasmaMap[K, V](store, flags)

  override val prover: PersistentBatchAVLProver[Digest32, Blake2b256.type] =
    localMap.prover

  Paideia.current.liveMaps.add(this)

  /** Records a mempool fork under its tree digest, evicting the oldest entry (by
    * insertion order) once the map exceeds maxMempoolMaps. Unbounded growth here was a
    * latent leak: every mempool fork parks a full BatchAVLProver and nothing but the
    * inPlace branch ever removed one.
    */
  private def putMempoolMap(key: List[Byte], value: PlasmaMapWithMap[K, V]): Unit = {
    mempoolMaps.put(key, value)
    while (mempoolMaps.size > maxMempoolMaps) {
      val oldestKey = mempoolMaps.iterator.next()._1
      mempoolMaps.remove(oldestKey)
    }
  }

  def clearMempoolMaps(): Unit = mempoolMaps.clear()

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
        putMempoolMap(map.prover.digest.toList, map)
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
      case Left(onDigest) => putMempoolMap(map.prover.digest.toList, map)
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
      case Left(onDigest) => putMempoolMap(map.digest.toList, map)
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
      case Left(onDigest) => putMempoolMap(map.digest.toList, map)
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

  def insertOrUpdate(keyVals: (K, V)*): ProvenResult[V] = ???

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
      opQueue.clone(),
      maxMempoolMaps
    )
  }

  /** Snapshot of the current mempool forks (digest -> in-progress tree), for callers
    * that need to carry pending, unconfirmed off-chain state across to a differently
    * backed map (e.g. StakingState.clone transplanting in-flight mempool chains onto a
    * freshly persisted snapshot). Independent copies, so mutating the result (or this
    * map's own forks afterwards) can't cross-contaminate the two maps.
    */
  def mempoolMapEntries: Seq[(List[Byte], PlasmaMapWithMap[K, V])] =
    mempoolMaps.toSeq.map(kv => (kv._1, kv._2.copy()))

  /** Adopts a set of mempool forks (as returned by mempoolMapEntries) into this map,
    * subject to the usual maxMempoolMaps eviction.
    */
  def adoptMempoolMaps(entries: Seq[(List[Byte], PlasmaMapWithMap[K, V])]): Unit =
    entries.foreach { case (k, v) => putMempoolMap(k, v) }

  /** Digest of the tree as actually persisted through localMap/the versioned prover, as
    * opposed to `digest`, which reflects the in-memory confirmed state (identical once
    * `commit()` has drained the queue).
    */
  def persistedDigest: ADDigest = localMap.digest

  /** Releases the underlying LevelDB handle (when storage is a VersionedLDBAVLStorage),
    * so the same directory can be reopened by a new store/map afterwards - e.g. to
    * prove a restore actually reads back from disk rather than from this instance's
    * in-memory state. VersionedLDBAVLStorage doesn't expose its LDBVersionedStore
    * publicly, hence the reflection. A no-op for any other storage implementation.
    */
  def close(): Unit = {
    storage match {
      case ldbStorage: VersionedLDBAVLStorage =>
        val field = classOf[VersionedLDBAVLStorage].getDeclaredField("store")
        field.setAccessible(true)
        field.get(ldbStorage).asInstanceOf[LDBVersionedStore].close()
      case _ => ()
    }
  }

  /** Number of confirmed-height batches still waiting to be applied to localMap by
    * commit().
    */
  def pendingOps: Int = opQueue.size

  /** Drains opQueue in FIFO order, applying every queued confirmed-height batch to
    * localMap so it persists through the versioned AVL+ prover. Until this is called,
    * confirmed writes only ever land in the in-memory newlyConfirmedMap and opQueue
    * grows forever with nothing behind it on disk.
    *
    * A no-op when the map was never initiated (nothing confirmed yet, so the queue is
    * necessarily empty). Otherwise asserts that the freshly persisted digest agrees
    * with the in-memory confirmed digest, since the whole point of committing is that
    * the two must never diverge.
    */
  def commit(): Unit = {
    while (opQueue.nonEmpty) {
      val (_, batch) = opQueue.dequeue()
      batch match {
        case InsertBatch(keyVals) => localMap.insert(keyVals: _*)
        case UpdateBatch(keyVals) => localMap.update(keyVals: _*)
        case DeleteBatch(keys)    => localMap.delete(keys: _*)
        case other =>
          throw new IllegalStateException(
            s"MempoolPlasmaMap.commit: unrecognized batch operation $other"
          )
      }
    }
    newlyConfirmedMap.foreach { confirmed =>
      val persisted = persistedDigest
      val inMemory  = confirmed.digest
      if (!(persisted sameElements inMemory)) {
        throw new IllegalStateException(
          "MempoolPlasmaMap.commit: persisted digest " +
            Util.bytes2hex(persisted) +
            " does not match in-memory confirmed digest " +
            Util.bytes2hex(inMemory)
        )
      }
    }
  }

  /** Clones the current confirmed tree into a brand-new (empty) store, producing an
    * independent MempoolPlasmaMap whose localMap and newlyConfirmedMap both carry a
    * structure-preserving copy of this tree (same digest, no shared mutable state, no
    * opQueue/mempoolMaps carried over). Unlike copy(newStore), which shares this map's
    * confirmed/mempool state going forward, cloneInto is meant for point-in-time
    * snapshots (e.g. StakingState.clone) that must reproduce the exact on-chain digest,
    * since AVL+ tree shape depends on operation history and can't be rebuilt by
    * re-inserting records.
    */
  private def markSubtreeNew(node: ProverNodes[Digest32]): Unit = {
    node.isNew = true
    node match {
      case internal: InternalProverNode[Digest32] =>
        markSubtreeNew(internal.left)
        markSubtreeNew(internal.right)
      case _ => ()
    }
  }

  private def markWholeTreeNew(
    prover: BatchAVLProver[Digest32, Blake2b256.type]
  ): Unit =
    markSubtreeNew(
      MempoolPlasmaMap.batchAVLProverTopNode
        .invoke(prover)
        .asInstanceOf[ProverNodes[Digest32]]
    )

  def cloneInto(
    newStore: VersionedAVLStorage[Digest32],
    paranoidChecks: Boolean = false
  ): MempoolPlasmaMap[K, V] = {
    val confirmed = newlyConfirmedMap.getOrElse(initiate())
    val treeCopy  = confirmed.copy()
    // treeCopy's nodes came out of a manifest round-trip (PlasmaMap.copy), which
    // reconstructs them as already-known/persisted state (isNew = false) rather than
    // freshly created state. VersionedAVLStorage.update only walks and writes nodes
    // that are new (or the root), so left as-is only the root would ever reach newStore
    // - fine for a single-leaf tree, silently incomplete for anything with real
    // internal structure. Since treeCopy is already an independent copy (not aliased
    // with confirmed's live tree), it's safe to mark its whole structure new so
    // PersistentBatchAVLProver.create's initial dump actually persists every node.
    markWholeTreeNew(treeCopy.prover)
    PersistentBatchAVLProver.create(treeCopy.prover, newStore, paranoidChecks).get
    val cloned = new MempoolPlasmaMap[K, V](
      newStore,
      flags,
      params,
      maxMempoolMaps = maxMempoolMaps
    )
    cloned.initiate()
    if (!(cloned.digest sameElements digest)) {
      throw new IllegalStateException(
        "MempoolPlasmaMap.cloneInto: clone digest " +
          Util.bytes2hex(cloned.digest) +
          " does not match source digest " +
          Util.bytes2hex(digest)
      )
    }
    cloned
  }
}

object MempoolPlasmaMap {

  /** BatchAVLProver.topNode is `protected` at the Scala level (even though the
    * generated bytecode is public), so cloneInto's isNew-marking walk (see
    * markWholeTreeNew) has to reach it through reflection instead of a direct call.
    */
  private val batchAVLProverTopNode =
    classOf[BatchAVLProver[_, _]].getMethod("topNode")

  /** Commits every live MempoolPlasmaMap of Paideia.current (see PaideiaSession.liveMaps)
    * and returns how many were committed. Intended to be called once per confirmed
    * block, after all of that block's transactions have been handled, so every
    * confirmed mutation queued during the block gets persisted in one pass. See
    * Paideia.commit()/PaideiaSession.commit().
    */
  def commitAll(): Int = Paideia.current.commit()

  /** Closes every live MempoolPlasmaMap's underlying LevelDB handle (see
    * MempoolPlasmaMap.close()) of Paideia.current, releasing every LOCK file so the
    * same directories can be reopened by fresh stores afterwards. See
    * Paideia.clearRegistries/PaideiaSession.close().
    */
  def closeAll(): Unit = Paideia.current.close()

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
