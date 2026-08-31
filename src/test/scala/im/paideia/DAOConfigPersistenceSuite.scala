package im.paideia

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.util.Util
import im.paideia.util.ConfKeys
import im.paideia.common.PaideiaSessionFixture

/** Covers DAOConfig.keys being restored from the persisted tree (deliverable 3):
  * before this fix, keys was an in-memory-only Set[String] populated solely by set(),
  * so every DAOConfig reconstructed after a restart would treat every key as unseen
  * and always insert, which fails (or corrupts the tree) once that key already exists
  * on disk.
  */
class DAOConfigPersistenceSuite extends AnyFunSuite with PaideiaSessionFixture {

  test(
    "keys is populated from the persisted tree on construction, and set() on a " +
      "restored key updates instead of re-inserting"
  ) {
    val daoKey  = Util.randomKey
    val config1 = DAOConfig(daoKey)
    assert(config1.keys.isEmpty)

    config1.set(ConfKeys.im_paideia_fees_createdao_erg, 1000000000L)
    config1.set(ConfKeys.im_paideia_fees_createdao_paideia, 100L)
    assert(config1.keys.size == 2)

    config1._config.commit()
    val digestAfterInitialSets = config1._config.digest
    config1._config.close()

    val config2 = DAOConfig(daoKey)
    assert(config2.keys.size == 2)
    assert(config2._config.digest sameElements digestAfterInitialSets)
    assert(config2.apply[Long](ConfKeys.im_paideia_fees_createdao_erg) == 1000000000L)
    assert(config2.apply[Long](ConfKeys.im_paideia_fees_createdao_paideia) == 100L)

    // set() on a key restored from disk (never seen via set() in this instance) must
    // update rather than insert. An identical value should leave the digest unchanged;
    // a different value should update it and be readable back afterwards.
    config2.set(ConfKeys.im_paideia_fees_createdao_erg, 1000000000L)
    config2._config.commit()
    assert(config2._config.digest sameElements digestAfterInitialSets)
    assert(config2.keys.size == 2)

    config2.set(ConfKeys.im_paideia_fees_createdao_erg, 2000000000L)
    config2._config.commit()
    assert(!(config2._config.digest sameElements digestAfterInitialSets))
    assert(config2.keys.size == 2)
    assert(config2.apply[Long](ConfKeys.im_paideia_fees_createdao_erg) == 2000000000L)

    config2._config.close()
  }

  test("keys stays empty for a brand new (empty) store, without throwing") {
    val daoKey = Util.randomKey
    val config = DAOConfig(daoKey)
    assert(config.keys.isEmpty)
    config._config.close()
  }
}
