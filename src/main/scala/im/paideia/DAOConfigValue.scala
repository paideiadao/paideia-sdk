package im.paideia

import special.collection.Coll
import special.collection.CollOverArray
import com.google.common.primitives._
import java.nio.charset.StandardCharsets
import scalan.RType
import scala.reflect.runtime.universe._
import scala.reflect.runtime.universe.weakTypeTag
import shapeless.Lazy
import com.google.j2objc.annotations.Weak
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.NetworkType

class DAOConfigValue(val value: Any, val enc: DAOConfigValueSerializer[_])

object DAOConfigValue {
    val byteTypeCode: Byte = 0
    val shortTypeCode: Byte = 1
    val intTypeCode: Byte = 2
    val longTypeCode: Byte = 3
    val bigIntTypeCode: Byte = 4
    val booleanTypeCode: Byte = 5
    val stringTypeCode: Byte = 6
    val contractSignatureTypeCode: Byte = 7
    val collTypeCode: Byte = 10
    val tupleTypeCode: Byte = 20

    def apply[T](value: T)(implicit enc: Lazy[DAOConfigValueSerializer[T]]): DAOConfigValue = new DAOConfigValue(value,enc.value)
}

trait DAOConfigValueSerializer[A] {
  def serialize(value: Any, includeType: Boolean): Array[Byte]
  val typeCode: Byte
}

object DAOConfigValueSerializer {

    def apply[A](value: A, includeType: Boolean = true)(implicit enc: Lazy[DAOConfigValueSerializer[A]]): Array[Byte] =
        enc.value.serialize(value, includeType)

    def instance[A](_typeCode: Byte, func: (A, Boolean) => Array[Byte]): DAOConfigValueSerializer[A] =
        new DAOConfigValueSerializer[A] {
            def serialize(value: Any, includeType: Boolean): Array[Byte] =
                func(value.asInstanceOf[A], includeType)
            val typeCode = _typeCode
        }

    implicit lazy val byteSerializer: DAOConfigValueSerializer[Byte] = {
        instance(DAOConfigValue.byteTypeCode,(b: Byte, includeType: Boolean) => 
            if (includeType) Array(DAOConfigValue.byteTypeCode,b)
            else Array(b)
        )
    }

    implicit lazy val shortSerializer: DAOConfigValueSerializer[Short] = {
        instance(DAOConfigValue.shortTypeCode,(s: Short, includeType: Boolean) => 
            if (includeType) Array(DAOConfigValue.shortTypeCode)++Shorts.toByteArray(s)
            else Shorts.toByteArray(s)
        )
    }

    implicit lazy val intSerializer: DAOConfigValueSerializer[Int] = {
        instance(DAOConfigValue.intTypeCode,(i: Int, includeType: Boolean) => 
            if (includeType) Array(DAOConfigValue.intTypeCode)++Ints.toByteArray(i)
            else Ints.toByteArray(i)
        )
    }

    implicit lazy val longSerializer: DAOConfigValueSerializer[Long] = {
        instance(DAOConfigValue.longTypeCode,(l: Long, includeType: Boolean) => 
            if (includeType) Array(DAOConfigValue.longTypeCode)++Longs.toByteArray(l)
            else Longs.toByteArray(l)
        )
    }

    implicit lazy val bigIntSerializer: DAOConfigValueSerializer[BigInt] = {
        instance(DAOConfigValue.bigIntTypeCode,(bi: BigInt, includeType: Boolean) => 
            if (includeType) Array(DAOConfigValue.bigIntTypeCode)++bi.toByteArray
            else bi.toByteArray
        )
    }

    implicit lazy val stringSerializer: DAOConfigValueSerializer[String] = {
        instance(DAOConfigValue.stringTypeCode,(s: String, includeType: Boolean) => {
                val stringData = s.getBytes(StandardCharsets.UTF_8)
                val data = Ints.toByteArray(stringData.size)++stringData
                if (includeType) Array(DAOConfigValue.stringTypeCode)++data
                else data
            }
        )
    }

    implicit lazy val paideiaContractSignatureSerializer: DAOConfigValueSerializer[PaideiaContractSignature] = {
        instance(DAOConfigValue.contractSignatureTypeCode,(pcs: PaideiaContractSignature, includeType: Boolean) => {
                val className = DAOConfigValueSerializer(pcs.className,false)
                val networkType = pcs.networkType.networkPrefix
                val version = DAOConfigValueSerializer(pcs.version,false)
                val data = pcs.contractHash.toArray++className++version++Array(networkType)
                if (includeType) Array(DAOConfigValue.contractSignatureTypeCode)++data
                else data
            }
        )
    }

    implicit lazy val booleanSerializer: DAOConfigValueSerializer[Boolean] = {
        instance(DAOConfigValue.booleanTypeCode,(b: Boolean, includeType: Boolean) => 
            if (includeType) Array(DAOConfigValue.booleanTypeCode,(if (b) 1.toByte else 0.toByte))
            else Array((if (b) 1.toByte else 0.toByte))
        )
    }

    implicit def arrSerializer[T](implicit enc: Lazy[DAOConfigValueSerializer[T]]): DAOConfigValueSerializer[Array[T]] = {
        instance(DAOConfigValue.collTypeCode,(c: Array[T], includeType: Boolean) => {
            val data = Array(enc.value.typeCode)++(Ints.toByteArray(c.size))++(c.flatMap{enc.value.serialize(_,false)})
            if (includeType) Array(DAOConfigValue.collTypeCode)++(data)
            else data
        })
    }

    implicit def tupleSerializer[T1,T2](implicit enc1: Lazy[DAOConfigValueSerializer[T1]], enc2: Lazy[DAOConfigValueSerializer[T2]]): DAOConfigValueSerializer[(T1,T2)] = {
        instance(DAOConfigValue.tupleTypeCode,(t: (T1,T2), includeType: Boolean) => {
            val dataLeft = enc1.value.serialize(t._1,true)
            val dataRight = enc2.value.serialize(t._2,true)
            if (includeType) Array(DAOConfigValue.tupleTypeCode)++dataLeft++dataRight
            else dataLeft++dataRight
        })
    }
}

class DAOConfigValueDeserializer(ba: Array[Byte]) {

    private var readerIndex: Int = 0
    private var peekIndex: Int = 0

    def readValue: Any = readValueTyped(readByte)

    def readValueTyped(tpe: Byte): Any = 
        tpe match {
            case DAOConfigValue.byteTypeCode => readByte
            case DAOConfigValue.shortTypeCode => readShort
            case DAOConfigValue.intTypeCode => readInt
            case DAOConfigValue.longTypeCode => readLong
            case DAOConfigValue.bigIntTypeCode => readBigInt
            case DAOConfigValue.booleanTypeCode => readBoolean
            case DAOConfigValue.stringTypeCode => readString
            case DAOConfigValue.contractSignatureTypeCode => readPaideiaContractSignature
            case DAOConfigValue.collTypeCode => readColl
            case DAOConfigValue.tupleTypeCode => readTuple
            case _ => throw new Exception("Unknown type code: " + tpe.toString)
        }

    def readByte: Byte = {
        readerIndex += 1
        ba(readerIndex-1)
    }

    def readShort: Short = {
        val res: Short = Shorts.fromByteArray(ba.slice(readerIndex,readerIndex+2).toArray)
        readerIndex+=2
        res
    }

    def readInt: Int = {
        val res: Int = Ints.fromByteArray(ba.slice(readerIndex,readerIndex+4).toArray)
        readerIndex+=4
        res
    }

    def readLong: Long = {
        val res: Long = Longs.fromByteArray(ba.slice(readerIndex,readerIndex+8).toArray)
        readerIndex+=8
        res
    }

    def readBigInt: BigInt = {
        val res: BigInt = BigInt(ba.slice(readerIndex,readerIndex+16).toArray)
        readerIndex+=16
        res
    }

    def readBoolean: Boolean = {
        val res: Boolean = ba(readerIndex)==1
        readerIndex+=1
        res
    }

    def readString: String = {
        val stringSize = readInt
        val res: String = new String(ba.slice(readerIndex,readerIndex+stringSize).toArray,StandardCharsets.UTF_8)
        readerIndex+=stringSize
        res
    }

    def readPaideiaContractSignature: PaideiaContractSignature = {
        val contractHash = Range.Int(0,32,1).flatMap{(_) => Array(readByte)}.toList
        val className = readString
        val version = readString
        val networkType = readByte match {
            case ErgoAddressEncoder.MainnetNetworkPrefix => NetworkType.MAINNET
            case ErgoAddressEncoder.TestnetNetworkPrefix => NetworkType.TESTNET
        }
        PaideiaContractSignature(className,version,networkType,contractHash)
    }

    def readColl: Array[_] = {
        val innerTpe: Byte = readByte
        val collSize: Int = readInt
        
        Range(0,collSize).map{(i: Int) => readValueTyped(innerTpe)}.toArray
    }

    def readTuple: (_,_) = {
        val left = readValue
        val right = readValue
        (left,right)
    }

}

object DAOConfigValueDeserializer {
    def deserialize(ba: Array[Byte]): Any = {
        new DAOConfigValueDeserializer(ba).readValue
    }
}