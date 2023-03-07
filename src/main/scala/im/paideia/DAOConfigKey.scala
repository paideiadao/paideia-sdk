package im.paideia

import special.collection.Coll
import scorex.crypto.hash.Blake2b256
import special.collection.CollOverArray
import java.nio.charset.StandardCharsets
import io.getblok.getblok_plasma.ByteConversion
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.settings.ErgoAlgos

class DAOConfigKey(_hashedKey: Array[Byte], _originalKey: Option[String] = None) {
    val originalKey: Option[String] = _originalKey
    def originalKeyBytes: Array[Byte] = originalKey.get.getBytes(StandardCharsets.UTF_8)
    val hashedKey: Array[Byte] = _hashedKey
    def ergoValue: ErgoValue[Coll[java.lang.Byte]] = ErgoValue.of(hashedKey)
}

object DAOConfigKey {
    def apply(s: String): DAOConfigKey = new DAOConfigKey(Blake2b256(s.getBytes(StandardCharsets.UTF_8)).array,Some(s))
    def apply(s: String, d: Array[Byte]): DAOConfigKey = new DAOConfigKey(Blake2b256(s.getBytes(StandardCharsets.UTF_8)++d).array,Some(s++ErgoAlgos.encode(d)))

    implicit val convertsDAOConfigKey: ByteConversion[DAOConfigKey] = new ByteConversion[DAOConfigKey] {
        override def convertToBytes(t: DAOConfigKey): Array[Byte] = t.hashedKey

        override def convertFromBytes(bytes: Array[Byte]): DAOConfigKey = new DAOConfigKey(bytes)
    }
}