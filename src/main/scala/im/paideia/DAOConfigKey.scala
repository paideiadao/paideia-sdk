package im.paideia

import sigma.Coll
import scorex.crypto.hash.Blake2b256
import java.nio.charset.StandardCharsets
import work.lithos.plasma.ByteConversion
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.settings.ErgoAlgos
import scala.collection.mutable.HashMap
import scala.collection.concurrent.TrieMap

class DAOConfigKey(
  _hashedKey: Array[Byte],
  _originalKey: Option[String] = None,
  _readOnly: Boolean           = false
) {

  /** None-branch: resolve a name for a key built from raw hashed bytes (e.g.
    * deserialized out of a persisted AVL+ tree) - first from this session's dynamic
    * registry, falling back to the process-global static ConfKeys names (covers a
    * session created after ConfKeys already registered them but before this session's
    * own knownKeys was seeded from staticNames - see PaideiaSession). Some-branch: a
    * key built BY NAME - register it into the current session's knownKeys. Names are
    * content-addressed (hash -> name is a pure function of the name), so a key that's
    * ALSO one of the static ConfKeys names being registered here too (redundantly with
    * DAOConfigKey.apply(s: String)'s own staticNames.putIfAbsent) is harmless.
    */
  val originalKey: Option[String] = _originalKey match {
    case None =>
      Paideia.current.knownKeys
        .getOrElse(_hashedKey.toList, None)
        .orElse(DAOConfigKey.staticNames.get(_hashedKey.toList))
    case Some(value) =>
      Paideia.current.knownKeys.put(_hashedKey.toList, _originalKey)
      _originalKey
  }
  val hashedKey: Array[Byte] = _hashedKey

  val readOnly: Boolean = _readOnly

  def originalKeyBytes: Array[Byte] = originalKey.get.getBytes(StandardCharsets.UTF_8)
  def ergoValue: ErgoValue[Coll[java.lang.Byte]] = ErgoValue.of(hashedKey)

  override def equals(x: Any): Boolean =
    x.isInstanceOf[DAOConfigKey] && x
      .asInstanceOf[DAOConfigKey]
      .hashedKey
      .toList
      .equals(hashedKey.toList)

  override def hashCode: Int = java.util.Arrays.hashCode(hashedKey)
}

object DAOConfigKey {

  /** Hash -> original key name for keys built from a fixed, well-known name (i.e. via
    * the single-arg apply below) - process-global rather than per-session, because
    * ConfKeys is an `object` whose ~100 `val`s construct named keys at class-init time,
    * i.e. the FIRST time anything touches ConfKeys, wherever that happens to be.
    * PaideiaSession seeds its own knownKeys from this map at construction time (so a
    * session created after ConfKeys was touched sees the standard names in its
    * knownKeys map view too), and DAOConfigKey's own constructor falls back to this map
    * for a session created before that point. Populated by DAOConfigKey.apply(s: String)
    * ONLY (the two-arg apply(s, bytes), for dynamic per-proposal/per-action keys, and
    * any other named construction, register into the current session's knownKeys only -
    * see DAOConfigKey's own constructor). Concurrent because suites run in parallel and
    * can all touch ConfKeys at once.
    */
  val staticNames: TrieMap[List[Byte], String] = TrieMap[List[Byte], String]()

  def apply(s: String): DAOConfigKey = {
    val hashedKey = Blake2b256(s.getBytes(StandardCharsets.UTF_8)).array
    staticNames.putIfAbsent(hashedKey.toList, s)
    new DAOConfigKey(hashedKey, Some(s))
  }
  def apply(s: String, d: Array[Byte]): DAOConfigKey = new DAOConfigKey(
    Blake2b256(s.getBytes(StandardCharsets.UTF_8) ++ d).array,
    Some(s ++ ErgoAlgos.encode(d))
  )

  /** Facade over Paideia.current.knownKeys (the per-session name registry - seeded from
    * staticNames at session construction time, and further populated by every by-name
    * DAOConfigKey construction since), kept for source compatibility with every
    * existing caller (paideia-state's getDAOConfig endpoint, persistState, tests, ...).
    */
  def knownKeys: HashMap[List[Byte], Option[String]] = Paideia.current.knownKeys

  implicit val convertsDAOConfigKey: ByteConversion[DAOConfigKey] =
    new ByteConversion[DAOConfigKey] {
      override def convertToBytes(t: DAOConfigKey): Array[Byte] = t.hashedKey

      override def convertFromBytes(bytes: Array[Byte]): DAOConfigKey = new DAOConfigKey(
        bytes
      )
    }
}
