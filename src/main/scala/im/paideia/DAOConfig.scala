package im.paideia

import im.paideia.util.Util
import scala.collection.mutable.HashMap
import scala.collection.mutable.Map
import special.collection.Coll
import org.ergoplatform.appkit.ErgoId
import im.paideia.common.contracts._
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.NetworkType
import im.paideia.staking.contracts._
import io.getblok.getblok_plasma.collections.PlasmaMap
import sigmastate.AvlTreeFlags
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.ByteConversion
import shapeless.Lazy
import scala.reflect.ClassTag
import scala.collection.mutable

case class DAOConfig(
   val _config: PlasmaMap[DAOConfigKey,Array[Byte]]
) {
    var keys = mutable.Set[String]()

    def apply[T](key: String): T = {
        apply[T](DAOConfigKey(key))
    }

    def apply[T](key: DAOConfigKey): T = {
        val check = _config.lookUp(key).response.head.tryOp.get.get
        val deserialized = DAOConfigValueDeserializer.deserialize(check)
        deserialized.asInstanceOf[T]
    }

    def getArray[T](key: String)(implicit tag: ClassTag[T]): Array[T] = {
        getArray[T](DAOConfigKey(key))
    }

    def getArray[T](key: DAOConfigKey)(implicit tag: ClassTag[T]): Array[T] = {
        apply[Array[Object]](key).map(_.asInstanceOf[T]).toArray
    }

    def set[T](key: String, value: T)(implicit enc: DAOConfigValueSerializer[T]) = {
        keys.add(key)
        _config.insert((DAOConfigKey(key),enc.serialize(value,true).toArray))
    }

    def set[T](key: DAOConfigKey, value: T)(implicit enc: DAOConfigValueSerializer[T]) = {
        if (keys.contains(key.originalKey.getOrElse(""))) {
            _config.update((key,enc.serialize(value,true).toArray))
        } else {
            keys.add(key.originalKey.getOrElse(""))
            _config.insert((key,enc.serialize(value,true).toArray))
        }
    }

    def handleExtension(extension: Map[String,String]) = {
        val todo = "update config based on extension"
    }

    def getProof(keys: String*): ErgoValue[Coll[java.lang.Byte]] = {
        getProof(keys.map(DAOConfigKey(_)):_*)
    }

    def getProof(keys: DAOConfigKey*)(implicit dummy: DummyImplicit): ErgoValue[Coll[java.lang.Byte]] = {
        val provRes = _config.lookUp(keys: _*)
        provRes.proof.ergoValue
    }

    def insertProof(operations: (String,Array[Byte])*): ErgoValue[Coll[java.lang.Byte]] = {
        insertProof(operations.map((kv: (String,Array[Byte])) => (DAOConfigKey(kv._1),kv._2)):_*)
    }

    def insertProof(operations: (DAOConfigKey,Array[Byte])*)(implicit dummy: DummyImplicit): ErgoValue[Coll[java.lang.Byte]] = {
        val provRes = _config.insert(operations: _*)
        provRes.proof.ergoValue
    }
}

object DAOConfig {
    def apply() : DAOConfig = {
        new DAOConfig(new PlasmaMap[DAOConfigKey,Array[Byte]](AvlTreeFlags.AllOperationsAllowed,PlasmaParameters.default))
    }

    implicit val convertDAOConfigKey: ByteConversion[DAOConfigKey] = new ByteConversion[DAOConfigKey] {
        def convertToBytes(t: DAOConfigKey): Array[Byte] = t.hashedKey
        
        def convertFromBytes(bytes: Array[Byte]): DAOConfigKey = new DAOConfigKey(bytes)    
    }
}
