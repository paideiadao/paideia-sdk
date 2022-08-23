package im.paideia.common.contracts

import org.ergoplatform.appkit.NetworkType

case class DAOControlled(
    version: String = "latest", 
    constants: Map[String,Object] = Map[String,Object](), 
    networkType: NetworkType, 
    script: String) extends PaideiaContract {

        override def ergoScript: String = {
            val superScript = super.ergoScript
            val completeScript = superScript.replaceAll("_script",script)
            completeScript
        }

    }
