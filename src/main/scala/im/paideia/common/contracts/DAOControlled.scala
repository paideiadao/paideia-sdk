package im.paideia.common.contracts

import org.ergoplatform.appkit.NetworkType

class DAOControlled(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {

    override def ergoScript = {
        val superScript = super.ergoScript
        val completeScript = superScript.replaceAll("_script", ergoScript)
        completeScript
    }
}

object DAOControlled extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): DAOControlled = getContractInstance[DAOControlled](contractSignature,new DAOControlled(contractSignature))
}
