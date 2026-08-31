package im.paideia.util

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Files
import scorex.db.LDBVersionedStore
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import sigma.data.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.ByteConversion.convertsString
import work.lithos.plasma.ByteConversion.convertsLongVal
import im.paideia.Paideia
import im.paideia.PaideiaSession
import im.paideia.common.PaideiaSessionFixture

/** Covers MempoolPlasmaMap.commit(): the opQueue -> localMap drain that actually
  * persists confirmed mutations through the versioned AVL+ prover, which previously
  * was never invoked anywhere (opQueue only ever grew).
  */
class MempoolPlasmaMapPersistenceSuite extends AnyFunSuite with PaideiaSessionFixture {

  // PlasmaParameters.default fixes keySize at 32 bytes, and convertsString round-trips
  // through hex, so every key here must be a 64 hex-char (32 byte) string - anything
  // shorter fails the underlying AVL+ operation silently (Failure wrapped in OpResult,
  // digest left unchanged) rather than throwing.
  private def key(tag: String): String = (tag * 32).take(64)
  private def key(i: Int): String      = f"$i%064x"

  private def newStore(dir: java.io.File): (LDBVersionedStore, VersionedLDBAVLStorage) = {
    val ldbStore   = new LDBVersionedStore(dir, 10)
    val avlStorage = new VersionedLDBAVLStorage(ldbStore)
    (ldbStore, avlStorage)
  }

  private def newMap(
    avlStorage: VersionedLDBAVLStorage
  ): MempoolPlasmaMap[String, Long] =
    new MempoolPlasmaMap[String, Long](
      avlStorage,
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )

  test("commit is a no-op on a never-initiated (fresh, empty) map") {
    val dir               = Files.createTempDirectory("mempool-plasma-empty").toFile
    val (ldbStore, store) = newStore(dir)
    val map                = newMap(store)

    assert(map.pendingOps == 0)
    map.commit() // must not throw
    assert(map.persistedDigest sameElements map.digest)

    map.close()
    ldbStore.close()
  }

  test(
    "commit drains queued inserts/updates/deletes into localMap, and the resulting " +
      "digest survives closing and reopening the store; uncommitted ops are not on " +
      "disk until commit() runs"
  ) {
    val dir                 = Files.createTempDirectory("mempool-plasma-commit").toFile
    val (ldbStore1, store1) = newStore(dir)
    val map1                 = newMap(store1)

    val (ka, kb, kc) = (key("aa"), key("bb"), key("cc"))

    map1.insert((ka, 1L), (kb, 2L), (kc, 3L))
    assert(map1.pendingOps == 1) // one queued batch for the insert call
    assert(!(map1.persistedDigest sameElements map1.digest))

    map1.commit()
    assert(map1.pendingOps == 0)
    assert(map1.persistedDigest sameElements map1.digest)
    val digestAfterFirstCommit = map1.digest

    // Further confirmed ops queue up but must not touch localMap until committed.
    map1.update((ka, 10L))
    map1.delete(kb)
    assert(map1.pendingOps == 2)
    assert(map1.persistedDigest sameElements digestAfterFirstCommit)
    assert(!(map1.persistedDigest sameElements map1.digest))

    map1.commit()
    assert(map1.pendingOps == 0)
    val digestAfterSecondCommit = map1.digest
    assert(map1.persistedDigest sameElements digestAfterSecondCommit)
    assert(!(digestAfterSecondCommit sameElements digestAfterFirstCommit))

    map1.close()
    ldbStore1.close()

    // Reopen a brand new map on the same directory: only what was committed should
    // be there.
    val (ldbStore2, store2) = newStore(dir)
    val map2                 = newMap(store2)
    map2.initiate()

    assert(map2.digest sameElements digestAfterSecondCommit)
    assert(map2.lookUp(ka).response.head.tryOp.get.contains(10L))
    assert(map2.lookUp(kb).response.head.tryOp.get.isEmpty)
    assert(map2.lookUp(kc).response.head.tryOp.get.contains(3L))

    map2.close()
    ldbStore2.close()
  }

  test("commit throws IllegalStateException when the persisted and in-memory " +
    "confirmed digests disagree") {
    val dir               = Files.createTempDirectory("mempool-plasma-invariant").toFile
    val (ldbStore, store) = newStore(dir)
    val map                = newMap(store)

    map.insert((key("aa"), 1L))
    map.commit()

    // Build a genuinely different, valid confirmed-tree state via the well-exercised
    // Left(digest) mempool-fork path (same mechanism used throughout the codebase),
    // then swap it in as newlyConfirmedMap via reflection *without* going through
    // opQueue - simulating the invariant violation this check exists to catch.
    val divergentDigest = map.insertWithDigest((key("bb"), 2L))(Left(map.digest)).digest
    val divergentMap    = map.getMap(Some(divergentDigest)).get

    val field = classOf[MempoolPlasmaMap[_, _]].getDeclaredField("newlyConfirmedMap")
    field.setAccessible(true)
    field.set(map, Some(divergentMap))

    val ex = intercept[IllegalStateException] {
      map.commit()
    }
    assert(ex.getMessage.contains("persisted digest"))

    // Leave the map in a consistent state again: liveMaps is a WeakHashMap, so this
    // instance isn't guaranteed to be GC'd before another test in this suite runs (all
    // share one PaideiaSession - see PaideiaSessionFixture) - it shouldn't still be
    // broken if something later ends up walking this suite's live maps.
    field.set(map, None)
    map.commit() // no-op: newlyConfirmedMap is None again and opQueue is empty

    map.close()
    ldbStore.close()
  }

  test("Paideia.commit() drains every live MempoolPlasmaMap") {
    // PaideiaSessionFixture gives this whole suite one PaideiaSession, shared across
    // every test in this file - so this suite's session.liveMaps can still carry
    // earlier tests' (closed, but not necessarily GC'd - it's a WeakHashMap) maps.
    // Paideia.commit() sweeps every live map of Paideia.current, so bind a throwaway
    // session just for this test: the cheapest way to get a liveMaps registry that
    // contains exactly the two maps this test creates.
    Paideia.withSession(PaideiaSession()) {
      val dir1                = Files.createTempDirectory("mempool-plasma-global-1").toFile
      val dir2                = Files.createTempDirectory("mempool-plasma-global-2").toFile
      val (ldbStore1, store1) = newStore(dir1)
      val (ldbStore2, store2) = newStore(dir2)
      val mapA                 = newMap(store1)
      val mapB                 = newMap(store2)

      mapA.insert((key("aa"), 1L))
      mapB.insert((key("bb"), 2L))
      assert(mapA.pendingOps == 1)
      assert(mapB.pendingOps == 1)

      val committed = Paideia.commit()
      assert(committed == 2)

      assert(mapA.pendingOps == 0)
      assert(mapB.pendingOps == 0)
      assert(mapA.persistedDigest sameElements mapA.digest)
      assert(mapB.persistedDigest sameElements mapB.digest)

      mapA.close()
      ldbStore1.close()
      mapB.close()
      ldbStore2.close()
    }
  }

  test(
    "mempoolMaps evicts the oldest fork once maxMempoolMaps is exceeded, and " +
      "clearMempoolMaps empties it"
  ) {
    val dir               = Files.createTempDirectory("mempool-plasma-cap").toFile
    val (ldbStore, store) = newStore(dir)
    val map = new MempoolPlasmaMap[String, Long](
      store,
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default,
      maxMempoolMaps = 3
    )

    val confirmedDigest = map.digest
    val digests = (0 until 5).map { i =>
      map.insertWithDigest((key(i), i.toLong))(Left(confirmedDigest)).digest
    }

    assert(map.mempoolMapEntries.size == 3)
    val remaining = map.mempoolMapEntries.map(_._1).toSet
    assert(!remaining.contains(digests(0).toList))
    assert(!remaining.contains(digests(1).toList))
    assert(remaining.contains(digests(4).toList))

    map.clearMempoolMaps()
    assert(map.mempoolMapEntries.isEmpty)

    map.close()
    ldbStore.close()
  }
}
