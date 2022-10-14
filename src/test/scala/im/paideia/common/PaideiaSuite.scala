package im.paideia.common

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia

class PaideiaSuite extends AnyFunSuite {
    test("Test PaideiaActor instantiation") {
        val contract = Config(PaideiaContractSignature())
        val contractSig = contract.contractSignature
        val isItWorking = Paideia.instantiateActor(contractSig)
        print(isItWorking)
    }
}
