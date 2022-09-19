package im.paideia

import special.collection.Coll
import scorex.crypto.hash.Blake2b256
import special.collection.CollOverArray
import java.nio.charset.StandardCharsets
import io.getblok.getblok_plasma.ByteConversion

class DAOConfigKey(_hashedKey: Array[Byte], _originalKey: Option[String] = None) {
    val originalKey: Option[String] = _originalKey
    val hashedKey: Array[Byte] = _hashedKey
}

object DAOConfigKey {
    def apply(s: String): DAOConfigKey = new DAOConfigKey(Blake2b256(s.getBytes(StandardCharsets.UTF_8)).array,Some(s))

    implicit val convertsDAOConfigKey: ByteConversion[DAOConfigKey] = new ByteConversion[DAOConfigKey] {
        override def convertToBytes(t: DAOConfigKey): Array[Byte] = t.hashedKey

        override def convertFromBytes(bytes: Array[Byte]): DAOConfigKey = new DAOConfigKey(bytes)
    }
}