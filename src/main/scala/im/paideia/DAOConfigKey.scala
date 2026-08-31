package im.paideia

import sigma.Coll
import scorex.crypto.hash.Blake2b256
import java.nio.charset.StandardCharsets
import work.lithos.plasma.ByteConversion
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.settings.ErgoAlgos
import scala.collection.mutable.HashMap

class DAOConfigKey(
  _hashedKey: Array[Byte],
  _originalKey: Option[String] = None,
  _readOnly: Boolean           = false
) {
  val originalKey: Option[String] = _originalKey match {
    case None => DAOConfigKey.knownKeys.getOrElse(_hashedKey.toList, None)
    case Some(value) =>
      DAOConfigKey.knownKeys.put(_hashedKey.toList, _originalKey)
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
  def apply(s: String): DAOConfigKey =
    new DAOConfigKey(Blake2b256(s.getBytes(StandardCharsets.UTF_8)).array, Some(s))
  def apply(s: String, d: Array[Byte]): DAOConfigKey = new DAOConfigKey(
    Blake2b256(s.getBytes(StandardCharsets.UTF_8) ++ d).array,
    Some(s ++ ErgoAlgos.encode(d))
  )

  /** Hashed key -> original key name, for keys constructed from a name. Keyed by List[Byte]
    * because Array[Byte] has identity equality and would never hit.
    */
  val knownKeys: HashMap[List[Byte], Option[String]] =
    new HashMap[List[Byte], Option[String]]()

  implicit val convertsDAOConfigKey: ByteConversion[DAOConfigKey] =
    new ByteConversion[DAOConfigKey] {
      override def convertToBytes(t: DAOConfigKey): Array[Byte] = t.hashedKey

      override def convertFromBytes(bytes: Array[Byte]): DAOConfigKey = new DAOConfigKey(
        bytes
      )
    }
}
