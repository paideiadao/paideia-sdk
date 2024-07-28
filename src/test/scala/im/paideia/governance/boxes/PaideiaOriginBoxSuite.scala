package im.paideia.governance.boxes

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.ConfKeys
import im.paideia.governance.contracts.PaideiaOrigin
import im.paideia.util.Env
import im.paideia.Paideia

class PaideiaOriginBoxSuite extends PaideiaTestSuite {
  test("From input") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        val originalBox =
          PaideiaOrigin(ConfKeys.im_paideia_contracts_paideia_origin, Env.paideiaDaoKey)
            .box(ctx, Paideia.getConfig(Env.paideiaDaoKey), 1000L)
        val box =
          originalBox
            .inputBox()
        val fromInputBox = PaideiaOriginBox.fromInputBox(ctx, box)
        assert(originalBox === fromInputBox)
      }
    })
  }
}
