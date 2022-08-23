package im.paideia.util

import scala.util.Random

object Util {
    def bytes2hex(bytes: Array[Byte], sep: Option[String] = None): String = {
        sep match {
            case None => bytes.map("%02x".format(_)).mkString
            case _ => bytes.map("%02x".format(_)).mkString(sep.get)
        }
    }

    def randomKey: String = {
        val key = new Array[Byte](32)
        Random.nextBytes(key)
        bytes2hex(key)
    }
}
