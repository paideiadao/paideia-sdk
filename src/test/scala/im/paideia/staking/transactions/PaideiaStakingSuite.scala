package im.paideia.staking.transactions

import org.scalatest.funsuite.AnyFunSuite
import scala.util.Random

class PaideiaStakingSuite extends AnyFunSuite{
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
