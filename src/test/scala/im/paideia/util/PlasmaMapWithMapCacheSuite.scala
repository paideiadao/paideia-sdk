package im.paideia.util

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Files
import scorex.db.LDBVersionedStore
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import sigma.data.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.ByteConversion.convertsString
import work.lithos.plasma.ByteConversion.convertsLongVal
import im.paideia.common.PaideiaSessionFixture

/** Covers PlasmaMapWithMap.toMap's digest-keyed cache: it must serve a stale Map from
  * before a mutation only never, and it must reuse the cached instance (not rebuild)
  * across repeated reads at the same digest.
  */
class PlasmaMapWithMapCacheSuite extends AnyFunSuite with PaideiaSessionFixture {

  // PlasmaParameters.default fixes keySize at 32 bytes, and convertsString round-trips
  // through hex, so every key here must be a 64 hex-char (32 byte) string.
  private def key(i: Int): String = f"$i%064x"

  private def newMap(): PlasmaMapWithMap[String, Long] =
    new PlasmaMapWithMap[String, Long](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )

  test("toMap after inserts returns the new entries without any manual cachedMap reset") {
    val map = newMap()

    map.insert((key(1), 1L))
    assert(map.toMap == Map(key(1) -> 1L))

    map.insert((key(2), 2L))
    assert(map.toMap == Map(key(1) -> 1L, key(2) -> 2L))
  }

  test("two consecutive toMap calls at the same digest reuse the cached instance") {
    val map = newMap()
    map.insert((key(1), 1L))

    val first  = map.toMap
    val second = map.toMap

    assert(first eq second)
  }

  test("an external cachedMap = None assignment still forces a rebuild") {
    val map = newMap()
    map.insert((key(1), 1L))

    val first = map.toMap
    map.cachedMap = None
    val second = map.toMap

    // Same digest, so content is unchanged, but a cache miss always rebuilds a fresh
    // Map instance - proving the reset actually took the rebuild path rather than
    // silently continuing to serve the old (still-correct-here) instance.
    assert(second == first)
    assert(!(second eq first))
  }

  test(
    "via MempoolPlasmaMap: insert -> getMap/toMap -> insert more -> toMap reflects " +
      "the second insert"
  ) {
    val dir        = Files.createTempDirectory("plasma-map-with-map-cache").toFile
    val ldbStore   = new LDBVersionedStore(dir, 10)
    val avlStorage = new VersionedLDBAVLStorage(ldbStore)
    val map = new MempoolPlasmaMap[String, Long](
      avlStorage,
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )

    map.insert((key(1), 1L))
    val afterFirstInsert = map.getMap(None).get.toMap
    assert(afterFirstInsert == Map(key(1) -> 1L))

    map.insert((key(2), 2L))
    val afterSecondInsert = map.getMap(None).get.toMap
    assert(afterSecondInsert == Map(key(1) -> 1L, key(2) -> 2L))

    map.close()
    ldbStore.close()
  }
}
