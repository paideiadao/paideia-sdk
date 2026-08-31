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
  _readOnly: Boolean           = false,
  _static: Boolean             = false
) {

  /** None-branch: resolve a name for a key built from raw hashed bytes (e.g.
    * deserialized out of a persisted AVL+ tree) - first from this session's dynamic
    * registry, falling back to the process-global static ConfKeys names. Some-branch:
    * a key built BY NAME - register it (session-wide, unless this is the special static
    * registration done by DAOConfigKey.apply(s: String); see DAOConfigKey.staticNames).
    */
  val originalKey: Option[String] = _originalKey match {
    case None =>
      Paideia.current.knownKeys
        .getOrElse(_hashedKey.toList, None)
        .orElse(DAOConfigKey.staticNames.get(_hashedKey.toList))
    case Some(value) =>
      if (!_static) Paideia.current.knownKeys.put(_hashedKey.toList, _originalKey)
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
    * i.e. the FIRST time anything touches ConfKeys, wherever that happens to be. If
    * those registrations went into whichever session happened to be current at that
    * moment (like every other DAOConfigKey-by-name registration does), they'd be
    * nameless in every other session. Populated ONLY by DAOConfigKey.apply(s: String);
    * the two-arg apply(s, bytes) (dynamic per-proposal/per-action keys) and any other
    * named construction still register into the current session's knownKeys as before.
    * Concurrent because suites run in parallel and can all touch ConfKeys at once.
    */
  val staticNames: TrieMap[List[Byte], String] = TrieMap[List[Byte], String]()

  def apply(s: String): DAOConfigKey = {
    val hashedKey = Blake2b256(s.getBytes(StandardCharsets.UTF_8)).array
    staticNames.putIfAbsent(hashedKey.toList, s)
    new DAOConfigKey(hashedKey, Some(s), _static = true)
  }
  def apply(s: String, d: Array[Byte]): DAOConfigKey = new DAOConfigKey(
    Blake2b256(s.getBytes(StandardCharsets.UTF_8) ++ d).array,
    Some(s ++ ErgoAlgos.encode(d))
  )

  /** Facade over Paideia.current.knownKeys (the per-session dynamic-name registry),
    * kept for source compatibility with every existing caller (paideia-state's
    * persistState reader, tests, ...). Static ConfKeys names live in staticNames
    * instead and are resolved as a fallback inside DAOConfigKey's own constructor, not
    * through this map.
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
