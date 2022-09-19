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

case class DAOConfigValue[T](value: T)

object DAOConfigValue {
    val byteTypeCode: Byte = 0
    val shortTypeCode: Byte = 1
    val intTypeCode: Byte = 2
    val longTypeCode: Byte = 3
    val bigIntTypeCode: Byte = 4
    val booleanTypeCode: Byte = 5
    val stringTypeCode: Byte = 6
    val collTypeCode: Byte = 10
    val tupleTypeCode: Byte = 20
}

trait DAOConfigValueSerializer[A] {
  def serialize(value: A, includeType: Boolean): Coll[Byte]
  val typeCode: Byte
}

object DAOConfigValueSerializer {

    def apply[A](value: A, includeType: Boolean = true)(implicit enc: Lazy[DAOConfigValueSerializer[A]]): Coll[Byte] =
        enc.value.serialize(value, includeType)

    def instance[A](_typeCode: Byte, func: (A, Boolean) => Coll[Byte]): DAOConfigValueSerializer[A] =
        new DAOConfigValueSerializer[A] {
            def serialize(value: A, includeType: Boolean): Coll[Byte] =
                func(value, includeType)
            val typeCode = _typeCode
        }

    implicit lazy val byteSerializer: DAOConfigValueSerializer[Byte] = {
        instance(DAOConfigValue.byteTypeCode,(b: Byte, includeType: Boolean) => 
            if (includeType) new CollOverArray(Array(DAOConfigValue.byteTypeCode,b))
            else new CollOverArray(Array(b))
        )
    }

    implicit lazy val shortSerializer: DAOConfigValueSerializer[Short] = {
        instance(DAOConfigValue.shortTypeCode,(s: Short, includeType: Boolean) => 
            if (includeType) new CollOverArray(Array(DAOConfigValue.shortTypeCode)++Shorts.toByteArray(s))
            else new CollOverArray(Shorts.toByteArray(s))
        )
    }

    implicit lazy val intSerializer: DAOConfigValueSerializer[Int] = {
        instance(DAOConfigValue.intTypeCode,(i: Int, includeType: Boolean) => 
            if (includeType) new CollOverArray(Array(DAOConfigValue.intTypeCode)++Ints.toByteArray(i))
            else new CollOverArray(Ints.toByteArray(i))
        )
    }

    implicit lazy val longSerializer: DAOConfigValueSerializer[Long] = {
        instance(DAOConfigValue.longTypeCode,(l: Long, includeType: Boolean) => 
            if (includeType) new CollOverArray(Array(DAOConfigValue.longTypeCode)++Longs.toByteArray(l))
            else new CollOverArray(Longs.toByteArray(l))
        )
    }

    implicit lazy val bigIntSerializer: DAOConfigValueSerializer[BigInt] = {
        instance(DAOConfigValue.bigIntTypeCode,(bi: BigInt, includeType: Boolean) => 
            if (includeType) new CollOverArray(Array(DAOConfigValue.bigIntTypeCode)++bi.toByteArray)
            else new CollOverArray(bi.toByteArray)
        )
    }

    implicit lazy val stringSerializer: DAOConfigValueSerializer[String] = {
        instance(DAOConfigValue.stringTypeCode,(s: String, includeType: Boolean) => {
                val stringData = s.getBytes(StandardCharsets.UTF_8)
                val data = Ints.toByteArray(stringData.size)++stringData
                if (includeType) new CollOverArray(Array(DAOConfigValue.stringTypeCode)++data)
                else new CollOverArray(data)
            }
        )
    }

    implicit lazy val booleanSerializer: DAOConfigValueSerializer[Boolean] = {
        instance(DAOConfigValue.booleanTypeCode,(b: Boolean, includeType: Boolean) => 
            if (includeType) new CollOverArray(Array(DAOConfigValue.booleanTypeCode,(if (b) 1.toByte else 0.toByte)))
            else new CollOverArray(Array((if (b) 1.toByte else 0.toByte)))
        )
    }

    implicit def arrSerializer[T](implicit enc: Lazy[DAOConfigValueSerializer[T]]): DAOConfigValueSerializer[Array[T]] = {
        instance(DAOConfigValue.collTypeCode,(c: Array[T], includeType: Boolean) => {
            val data = new CollOverArray(Array(enc.value.typeCode)++(Ints.toByteArray(c.size))++(c.flatMap{enc.value.serialize(_,false).toArray}))
            if (includeType) new CollOverArray(Array(DAOConfigValue.collTypeCode)).append(data)
            else data
        })
    }

    implicit def tupleSerializer[T1,T2](implicit enc1: Lazy[DAOConfigValueSerializer[T1]], enc2: Lazy[DAOConfigValueSerializer[T2]]): DAOConfigValueSerializer[(T1,T2)] = {
        instance(DAOConfigValue.tupleTypeCode,(t: (T1,T2), includeType: Boolean) => {
            val dataLeft = enc1.value.serialize(t._1,true)
            val dataRight = enc2.value.serialize(t._2,true)
            if (includeType) new CollOverArray(Array(DAOConfigValue.tupleTypeCode)).append(dataLeft).append(dataRight)
            else dataLeft.append(dataRight)
        })
    }
}

class DAOConfigValueDeserializer(ba: Coll[Byte]) {

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
    def deserialize(ba: Coll[Byte]): Any = {
        new DAOConfigValueDeserializer(ba).readValue
    }
}