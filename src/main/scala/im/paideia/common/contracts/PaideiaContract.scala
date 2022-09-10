package im.paideia.common.contracts

import scala.io.Source
import sigmastate.Values
import org.ergoplatform.appkit.JavaHelpers
import org.ergoplatform.appkit.ErgoContract
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.NetworkType
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.ErgoValue
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import java.lang.{Byte => JByte}

trait PaideiaContract {
    val version: String
    val constants: Map[String,Object]
    val networkType: NetworkType

    def ergoScript: String = Source.fromResource("ergoscript/" + getClass.getSimpleName + "/" + version + "/" + getClass.getSimpleName + ".es").mkString
    def ergoTree: Values.ErgoTree = {
        val jConstants = constants.asJava
        JavaHelpers.compile(constants.asJava,ergoScript,networkType.networkPrefix)
    }
    def contract: ErgoContract = new ErgoTreeContract(ergoTree, networkType)

    def ergoValue: ErgoValue[(Coll[JByte],(Coll[JByte],Coll[JByte]))] = {
        ErgoValue.pairOf(
            ErgoValue.of(getClass().getCanonicalName().getBytes()),
            ErgoValue.pairOf(
                ErgoValue.of(version.getBytes()),
                ErgoValue.of(Blake2b256(ergoTree.bytes).array)
            )
        )
    }
}
