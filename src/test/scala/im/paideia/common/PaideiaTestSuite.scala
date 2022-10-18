package im.paideia.common

import org.scalatest.funsuite.AnyFunSuite
import scala.util.Random
import im.paideia.HttpClientTesting
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.util.Env
import im.paideia.DAO
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts.Config
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.PaideiaOrigin
import org.ergoplatform.appkit.ErgoId
import im.paideia.governance.contracts.ProtoDAOProxy

class PaideiaTestSuite extends AnyFunSuite with HttpClientTesting {
    
}

object PaideiaTestSuite {
    var initializedPaideia: Boolean = false

    def init(ctx: BlockchainContextImpl) = {
        if (!initializedPaideia) {
            val paideiaConfig = DAOConfig()
            paideiaConfig.set("im.paideia.fees.createDAO.erg",1000000000L)
            paideiaConfig.set("im.paideia.fees.createDAO.paideia",100L)
            paideiaConfig.set("im.paideia.daoKey",ErgoId.create(Env.paideiaDaoKey).getBytes())
            Paideia.addDAO(DAO(Env.paideiaDaoKey,paideiaConfig))
            val configContract = Config(PaideiaContractSignature("im.paideia.common.contracts.Config"))
            val paideiaOriginContract = PaideiaOrigin(PaideiaContractSignature("im.paideia.governance.contracts.PaideiaOrigin"))
            val protoDaoProxyContract = ProtoDAOProxy(PaideiaContractSignature("im.paideia.governance.contracts.ProtoDAOProxy"))
            Paideia.instantiateActor(configContract.contractSignature)
            Paideia.instantiateActor(PaideiaContractSignature("im.paideia.common.contracts.Treasury"))
            Paideia.instantiateActor(paideiaOriginContract.contractSignature)
            Paideia.instantiateActor(PaideiaContractSignature("im.paideia.governance.contracts.ProtoDAO"))
            Paideia.instantiateActor(protoDaoProxyContract.contractSignature)
            configContract.newBox(configContract.box(ctx,paideiaConfig).inputBox(),false)
            paideiaOriginContract.newBox(paideiaOriginContract.box(ctx,paideiaConfig,1000000L).inputBox(),false)
            initializedPaideia = true
        }
    }
}