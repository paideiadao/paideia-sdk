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
import com.github.tototoshi.csv.DefaultCSVFormat
import java.io.StringWriter
import com.github.tototoshi.csv.CSVWriter
import com.github.tototoshi.csv.Quoting
import com.github.tototoshi.csv.QUOTE_NONNUMERIC
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.sdk.JavaHelpers
import com.github.tototoshi.csv.CSVReader
import java.io.StringReader

case class DAOConfigValue[T](val valueType: Byte, val value: T)

//(using T <:< (Byte | Short | Int | Long | BigInt | Boolean | String | PaideiaContractSignature | Array))

object DAOConfigValue {
  val byteTypeCode: Byte              = 0
  val shortTypeCode: Byte             = 1
  val intTypeCode: Byte               = 2
  val longTypeCode: Byte              = 3
  val bigIntTypeCode: Byte            = 4
  val booleanTypeCode: Byte           = 5
  val stringTypeCode: Byte            = 6
  val contractSignatureTypeCode: Byte = 7
  val collTypeCode: Byte              = 10
  val tupleTypeCode: Byte             = 20

  val readOnlyCode: Byte = -128

  // def apply[T](value: T)(implicit
  //   enc: Lazy[DAOConfigValueSerializer[T]]
  // ): DAOConfigValue = new DAOConfigValue(value, enc.value)
}

trait DAOConfigValueSerializer[A] {
  def serialize(value: Any, includeType: Boolean, readOnly: Boolean): Array[Byte]
  val typeCode: Byte
}

object DAOConfigValueSerializer {

  implicit object MyFormat extends DefaultCSVFormat {
    override val lineTerminator   = ""
    override val quoting: Quoting = QUOTE_NONNUMERIC
  }

  def apply[A](value: A, includeType: Boolean = true, readOnly: Boolean = false)(implicit
    enc: Lazy[DAOConfigValueSerializer[A]]
  ): Array[Byte] =
    enc.value.serialize(value, includeType, readOnly)

  def instance[A](
    _typeCode: Byte,
    func: (A, Boolean, Boolean) => Array[Byte]
  ): DAOConfigValueSerializer[A] =
    new DAOConfigValueSerializer[A] {
      def serialize(value: Any, includeType: Boolean, readOnly: Boolean): Array[Byte] =
        func(value.asInstanceOf[A], includeType, readOnly)
      val typeCode = _typeCode
    }

  implicit lazy val byteSerializer: DAOConfigValueSerializer[Byte] = {
    instance(
      DAOConfigValue.byteTypeCode,
      (b: Byte, includeType: Boolean, readOnly: Boolean) =>
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.byteTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.byteTypeCode,
            b
          )
        else Array(b)
    )
  }

  implicit lazy val shortSerializer: DAOConfigValueSerializer[Short] = {
    instance(
      DAOConfigValue.shortTypeCode,
      (s: Short, includeType: Boolean, readOnly: Boolean) =>
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.shortTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.shortTypeCode
          ) ++ Shorts.toByteArray(s)
        else Shorts.toByteArray(s)
    )
  }

  implicit lazy val intSerializer: DAOConfigValueSerializer[Int] = {
    instance(
      DAOConfigValue.intTypeCode,
      (i: Int, includeType: Boolean, readOnly: Boolean) =>
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.intTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.intTypeCode
          ) ++ Ints.toByteArray(i)
        else Ints.toByteArray(i)
    )
  }

  implicit lazy val longSerializer: DAOConfigValueSerializer[Long] = {
    instance(
      DAOConfigValue.longTypeCode,
      (l: Long, includeType: Boolean, readOnly: Boolean) =>
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.longTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.longTypeCode
          ) ++ Longs.toByteArray(l)
        else Longs.toByteArray(l)
    )
  }

  implicit lazy val bigIntSerializer: DAOConfigValueSerializer[BigInt] = {
    instance(
      DAOConfigValue.bigIntTypeCode,
      (bi: BigInt, includeType: Boolean, readOnly: Boolean) =>
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.bigIntTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.bigIntTypeCode
          ) ++ bi.toByteArray
        else bi.toByteArray
    )
  }

  implicit lazy val stringSerializer: DAOConfigValueSerializer[String] = {
    instance(
      DAOConfigValue.stringTypeCode,
      (s: String, includeType: Boolean, readOnly: Boolean) => {
        val stringData = s.getBytes(StandardCharsets.UTF_8)
        val data       = Ints.toByteArray(stringData.size) ++ stringData
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.stringTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.stringTypeCode
          ) ++ data
        else data
      }
    )
  }

  implicit lazy val paideiaContractSignatureSerializer
    : DAOConfigValueSerializer[PaideiaContractSignature] = {
    instance(
      DAOConfigValue.contractSignatureTypeCode,
      (pcs: PaideiaContractSignature, includeType: Boolean, readOnly: Boolean) => {
        val className   = DAOConfigValueSerializer(pcs.className, false)
        val networkType = pcs.networkType.networkPrefix
        val version     = DAOConfigValueSerializer(pcs.version, false)
        val data = pcs.contractHash.toArray ++ className ++ version ++ Array(networkType)
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.contractSignatureTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.contractSignatureTypeCode
          ) ++ data
        else data
      }
    )
  }

  implicit lazy val booleanSerializer: DAOConfigValueSerializer[Boolean] = {
    instance(
      DAOConfigValue.booleanTypeCode,
      (b: Boolean, includeType: Boolean, readOnly: Boolean) =>
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.booleanTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.booleanTypeCode,
            (if (b) 1.toByte else 0.toByte)
          )
        else Array((if (b) 1.toByte else 0.toByte))
    )
  }

  implicit def arrSerializer[T](implicit
    enc: Lazy[DAOConfigValueSerializer[T]]
  ): DAOConfigValueSerializer[Array[T]] = {
    instance(
      DAOConfigValue.collTypeCode,
      (c: Array[T], includeType: Boolean, readOnly: Boolean) => {
        val data = Array(enc.value.typeCode) ++ (Ints.toByteArray(c.size)) ++ (c.flatMap {
          enc.value.serialize(_, false, false)
        })
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.collTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.collTypeCode
          ) ++ (data)
        else data
      }
    )
  }

  implicit def tupleSerializer[T1, T2](implicit
    enc1: Lazy[DAOConfigValueSerializer[T1]],
    enc2: Lazy[DAOConfigValueSerializer[T2]]
  ): DAOConfigValueSerializer[(T1, T2)] = {
    instance(
      DAOConfigValue.tupleTypeCode,
      (t: (T1, T2), includeType: Boolean, readOnly: Boolean) => {
        val dataLeft  = enc1.value.serialize(t._1, true, false)
        val dataRight = enc2.value.serialize(t._2, true, false)
        if (includeType)
          Array(
            if (readOnly)
              (DAOConfigValue.tupleTypeCode | DAOConfigValue.readOnlyCode).toByte
            else DAOConfigValue.tupleTypeCode
          ) ++ dataLeft ++ dataRight
        else dataLeft ++ dataRight
      }
    )
  }

  def stringType(stringType: String): Byte = {
    val collTypePattern  = """Coll\[(.*)\]""".r
    val tupleTypePattern = """\((.*)\)""".r

    stringType match {
      case "Byte"                     => DAOConfigValue.byteTypeCode
      case "Short"                    => DAOConfigValue.shortTypeCode
      case "Int"                      => DAOConfigValue.intTypeCode
      case "Long"                     => DAOConfigValue.longTypeCode
      case "BigInt"                   => DAOConfigValue.bigIntTypeCode
      case "Boolean"                  => DAOConfigValue.booleanTypeCode
      case "String"                   => DAOConfigValue.stringTypeCode
      case collTypePattern(_)         => DAOConfigValue.collTypeCode
      case tupleTypePattern(_)        => DAOConfigValue.tupleTypeCode
      case "PaideiaContractSignature" => DAOConfigValue.contractSignatureTypeCode
    }
  }

  def fromString(
    tpe: String,
    value: String,
    includeType: Boolean = true,
    stripQuotes: Boolean = false
  ): Array[Byte] = {
    val collTypePattern = """Coll\[(.*)\]""".r
    val collPattern     = """\[(.*)\]""".r

    val tupleTypePattern = """\((.*),(.*)\)""".r
    val tuplePattern     = """\((.*)\)""".r

    val contractSigPattern = """PaideiaContractSignature\((.*),(.*),(.*),(.*)\)""".r

    tpe match {
      case "Byte"    => DAOConfigValueSerializer(value.toByte, includeType)
      case "Short"   => DAOConfigValueSerializer(value.toShort, includeType)
      case "Int"     => DAOConfigValueSerializer(value.toInt, includeType)
      case "Long"    => DAOConfigValueSerializer(value.toLong, includeType)
      case "BigInt"  => DAOConfigValueSerializer(BigInt(value), includeType)
      case "Boolean" => DAOConfigValueSerializer(value.toBoolean, includeType)
      case "String" =>
        if (stripQuotes)
          DAOConfigValueSerializer(value.substring(1, value.size - 1), includeType)
        else DAOConfigValueSerializer(value, includeType)
      case "Coll[Byte]" =>
        DAOConfigValueSerializer(JavaHelpers.decodeStringToBytes(value), includeType)
      case "PaideiaContractSignature" =>
        value match {
          case contractSigPattern(className, version, network, contractHashHex) =>
            DAOConfigValueSerializer(
              PaideiaContractSignature(
                className,
                version,
                NetworkType.fromValue(network.toLowerCase()),
                JavaHelpers.decodeStringToBytes(contractHashHex).toList
              )
            )
        }
      case tupleTypePattern(leftType, rightType) =>
        (if (includeType) Array(DAOConfigValue.tupleTypeCode)
         else new Array[Byte](0)) ++ (value match {
          case tuplePattern(tupleValues) => {
            val csvReader = CSVReader.open(new StringReader(tupleValues))
            csvReader.readNext() match {
              case None => new Array[Byte](0)
              case Some(tupleValueList) =>
                fromString(leftType, tupleValueList(0)) ++ fromString(
                  rightType,
                  tupleValueList(1)
                )
            }
          }
        })
      case collTypePattern(innerType) => {
        value match {
          case collPattern(collValues) =>
            val csvReader = CSVReader.open(new StringReader(collValues))
            csvReader.readNext() match {
              case None => new Array[Byte](0)
              case Some(collValueList) =>
                (if (includeType) Array[Byte](DAOConfigValue.collTypeCode)
                 else new Array[Byte](0)) ++
                Array(stringType(innerType)) ++
                Ints.toByteArray(collValueList.size) ++
                collValueList
                  .flatMap(cv => fromString(innerType, cv, false))
            }
          case _ => new Array[Byte](0)
        }

      }
    }
  }
}

class DAOConfigValueDeserializer(ba: Array[Byte]) {

  implicit object MyFormat extends DefaultCSVFormat {
    override val lineTerminator   = ""
    override val quoting: Quoting = QUOTE_NONNUMERIC
  }

  private var readerIndex: Int = 0

  def readValue[T](
    setReaderIndex: Int = 0
  ): T = {
    readerIndex = setReaderIndex
    readValueTyped(readByte).asInstanceOf[T]
  }

  def readValueTyped(tpe: Byte): Any =
    tpe match {
      case DAOConfigValue.byteTypeCode              => readByte
      case DAOConfigValue.shortTypeCode             => readShort
      case DAOConfigValue.intTypeCode               => readInt
      case DAOConfigValue.longTypeCode              => readLong
      case DAOConfigValue.bigIntTypeCode            => readBigInt
      case DAOConfigValue.booleanTypeCode           => readBoolean
      case DAOConfigValue.stringTypeCode            => readString
      case DAOConfigValue.contractSignatureTypeCode => readPaideiaContractSignature
      case DAOConfigValue.collTypeCode              => readColl
      case DAOConfigValue.tupleTypeCode             => readTuple
      case _ => throw new Exception("Unknown type code: " + tpe.toString)
    }

  def readByte: Byte = {
    readerIndex += 1
    ba(readerIndex - 1)
  }

  def readShort: Short = {
    val res: Short = Shorts.fromByteArray(ba.slice(readerIndex, readerIndex + 2).toArray)
    readerIndex += 2
    res
  }

  def readInt: Int = {
    val res: Int = Ints.fromByteArray(ba.slice(readerIndex, readerIndex + 4).toArray)
    readerIndex += 4
    res
  }

  def readLong: Long = {
    val res: Long = Longs.fromByteArray(ba.slice(readerIndex, readerIndex + 8).toArray)
    readerIndex += 8
    res
  }

  def readBigInt: BigInt = {
    val res: BigInt = BigInt(ba.slice(readerIndex, readerIndex + 16).toArray)
    readerIndex += 16
    res
  }

  def readBoolean: Boolean = {
    val res: Boolean = ba(readerIndex) == 1
    readerIndex += 1
    res
  }

  def readString: String = {
    val stringSize = readInt
    val res: String = new String(
      ba.slice(readerIndex, readerIndex + stringSize).toArray,
      StandardCharsets.UTF_8
    )
    readerIndex += stringSize
    res
  }

  def readPaideiaContractSignature: PaideiaContractSignature = {
    val contractHash = Range.Int(0, 32, 1).flatMap { (_) => Array(readByte) }.toList
    val className    = readString
    val version      = readString
    val networkType = readByte match {
      case ErgoAddressEncoder.MainnetNetworkPrefix => NetworkType.MAINNET
      case ErgoAddressEncoder.TestnetNetworkPrefix => NetworkType.TESTNET
    }
    PaideiaContractSignature(className, version, networkType, contractHash)
  }

  def readColl: Array[_] = {
    val innerTpe: Byte = readByte
    val collSize: Int  = readInt

    Range(0, collSize).map { (i: Int) => readValueTyped(innerTpe) }.toArray
  }

  def readTuple: (_, _) = {
    val left: Any  = readValue(readerIndex)
    val right: Any = readValue(readerIndex)
    (left, right)
  }

  def readType(setReaderIndex: Int = 0): String = {
    readerIndex = setReaderIndex
    readTypeTyped(readByte)
  }

  def readTypeTyped(tpe: Byte, moveIndex: Boolean = true): String =
    tpe match {
      case DAOConfigValue.byteTypeCode => {
        if (moveIndex) readByte
        "Byte"
      }
      case DAOConfigValue.shortTypeCode => {
        if (moveIndex) readShort
        "Short"
      }
      case DAOConfigValue.intTypeCode => {
        if (moveIndex) readInt
        "Int"
      }
      case DAOConfigValue.longTypeCode => {
        if (moveIndex) readLong
        "Long"
      }
      case DAOConfigValue.bigIntTypeCode => {
        if (moveIndex) readBigInt
        "BigInt"
      }
      case DAOConfigValue.booleanTypeCode => {
        if (moveIndex) readBoolean
        "Boolean"
      }
      case DAOConfigValue.stringTypeCode => {
        if (moveIndex) readString
        "String"
      }
      case DAOConfigValue.contractSignatureTypeCode => {
        if (moveIndex) readPaideiaContractSignature
        "PaideiaContractSignature"
      }
      case DAOConfigValue.collTypeCode  => "Coll[" + readCollType + "]"
      case DAOConfigValue.tupleTypeCode => readTupleType
      case _ => throw new Exception("Unknown type code: " + tpe.toString)
    }

  def readCollType: String = {
    if (readerIndex >= ba.length) "?"
    else {
      val innerTpe: Byte = readByte
      val collSize: Int  = readInt

      Range(0, collSize)
        .map { (i: Int) => readTypeTyped(innerTpe) }
        .toArray
        .applyOrElse(0, (i: Int) => readTypeTyped(innerTpe, false))
    }

  }

  def readTupleType: String = {
    val left  = readType(readerIndex)
    val right = readType(readerIndex)
    "(" + left + "," + right + ")"
  }

  def collToString: String = {
    val innerTpe: Byte = readByte
    val collSize: Int  = readInt

    innerTpe match {
      case DAOConfigValue.byteTypeCode =>
        Range(0, collSize)
          .map { (i: Int) => readValueTyped(innerTpe) }
          .toArray
          .map("%02x" format _)
          .mkString
      case _ => {
        val sw        = new StringWriter()
        val csvWriter = CSVWriter.open(sw)

        csvWriter.writeRow(
          Range(0, collSize)
            .map { (i: Int) => toStringTyped(innerTpe) }
        )

        "[" + sw.toString + "]"
      }
    }
  }

  def tupleToString: String = {
    val leftTpe  = readByte
    val left     = toStringTyped(leftTpe)
    val rightTpe = readByte
    val right    = toStringTyped(rightTpe)

    val sw        = new StringWriter()
    val csvWriter = CSVWriter.open(sw)

    csvWriter.writeRow(List(left, right))

    "(" + sw.toString + ")"
  }

  def toStringTyped(tpe: Byte) = {
    tpe match {
      case DAOConfigValue.collTypeCode  => collToString
      case DAOConfigValue.tupleTypeCode => tupleToString
      case _                            => readValueTyped(tpe).toString()
    }
  }

  override def toString(): String = {
    readerIndex = 0
    toStringTyped(readByte)
  }

}

object DAOConfigValueDeserializer {
  def apply[T](ba: Array[Byte]): T =
    DAOConfigValueDeserializer.deserialize(ba)

  def deserialize[T](ba: Array[Byte]): T = {
    new DAOConfigValueDeserializer(ba).readValue()
  }

  def getType(ba: Array[Byte]): String = {
    new DAOConfigValueDeserializer(ba).readType()
  }

  def toString(ba: Array[Byte]): String = {
    new DAOConfigValueDeserializer(ba).toString
  }
}
