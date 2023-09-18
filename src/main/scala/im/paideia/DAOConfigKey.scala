package im.paideia

import special.collection.Coll
import scorex.crypto.hash.Blake2b256
import special.collection.CollOverArray
import java.nio.charset.StandardCharsets
import work.lithos.plasma.ByteConversion
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.settings.ErgoAlgos
import scala.collection.mutable.HashMap

class DAOConfigKey(_hashedKey: Array[Byte], _originalKey: Option[String] = None) {
  val originalKey: Option[String] = _originalKey match {
    case None => DAOConfigKey.knownKeys.getOrElse(_hashedKey, None)
    case Some(value) =>
      DAOConfigKey.knownKeys.put(_hashedKey, _originalKey)
      _originalKey
  }
  val hashedKey: Array[Byte] = _hashedKey

  def originalKeyBytes: Array[Byte] = originalKey.get.getBytes(StandardCharsets.UTF_8)
  def ergoValue: ErgoValue[Coll[java.lang.Byte]] = ErgoValue.of(hashedKey)

  override def equals(x: Any): Boolean =
    x.isInstanceOf[DAOConfigKey] && x
      .asInstanceOf[DAOConfigKey]
      .hashedKey
      .toList
      .equals(hashedKey.toList)
}

object DAOConfigKey {
  def apply(s: String): DAOConfigKey =
    new DAOConfigKey(Blake2b256(s.getBytes(StandardCharsets.UTF_8)).array, Some(s))
  def apply(s: String, d: Array[Byte]): DAOConfigKey = new DAOConfigKey(
    Blake2b256(s.getBytes(StandardCharsets.UTF_8) ++ d).array,
    Some(s ++ ErgoAlgos.encode(d))
  )

  val knownKeys: HashMap[Array[Byte], Option[String]] =
    new HashMap[Array[Byte], Option[String]]()

  implicit val convertsDAOConfigKey: ByteConversion[DAOConfigKey] =
    new ByteConversion[DAOConfigKey] {
      override def convertToBytes(t: DAOConfigKey): Array[Byte] = t.hashedKey

      override def convertFromBytes(bytes: Array[Byte]): DAOConfigKey = new DAOConfigKey(
        bytes
      )
    }
}
