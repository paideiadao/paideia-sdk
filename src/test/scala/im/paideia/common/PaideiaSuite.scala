package im.paideia.common

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.util.Env
import im.paideia.DAOConfig
import im.paideia.DAO
import im.paideia.util.ConfKeys
import im.paideia.util.Util
import org.ergoplatform.sdk.ErgoId

class PaideiaSuite extends AnyFunSuite {
  test("Test PaideiaActor instantiation") {
    val daoKey        = Util.randomKey
    val paideiaConfig = DAOConfig(daoKey)
    paideiaConfig.set(
      ConfKeys.im_paideia_dao_action_tokenid,
      ErgoId.create(Util.randomKey).getBytes
    )
    Paideia.addDAO(DAO(daoKey, paideiaConfig))
    val contract    = Config(PaideiaContractSignature(daoKey = daoKey))
    val contractSig = contract.contractSignature
    Config.clear
    Paideia._actorList -= contractSig.className
    val isItWorking = Paideia.instantiateContractInstance(contractSig)
    print(isItWorking)
  }
}
