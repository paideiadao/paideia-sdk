package im.paideia.util

import scala.util.Random
import scorex.util.encode.Base16

object Util {

  def bytes2hex(bytes: Array[Byte], sep: Option[String] = None): String = {
    sep match {
      case None => bytes.map("%02x".format(_)).mkString
      case _    => bytes.map("%02x".format(_)).mkString(sep.get)
    }
  }

  def hex2bytes(hex: String): Array[Byte] = Base16.decode(hex).get

  def randomKey: String = {
    val key = new Array[Byte](32)
    Random.nextBytes(key)
    bytes2hex(key)
  }
}
