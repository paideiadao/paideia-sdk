package im.paideia.common.transactions

import org.ergoplatform.appkit.UnsignedTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.ReducedTransaction
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.OutBox

trait PaideiaTransaction {
    var inputs: List[InputBox] = _
    var dataInputs: List[InputBox] = List[InputBox]()
    var outputs: List[OutBox] = _
    var tokensToBurn: List[ErgoToken] = List[ErgoToken]()
    var fee: Long = _
    var ctx: BlockchainContextImpl = _
    var changeAddress: ErgoAddress = _

    def unsigned(): UnsignedTransaction = {
        this.ctx.newTxBuilder()
            .outputs(this.outputs: _*)
            .sendChangeTo(this.changeAddress)
            .tokensToBurn(this.tokensToBurn: _*)
            .preHeader(this.ctx.createPreHeader().build())
            .fee(this.fee)
            .boxesToSpend(this.inputs.asJava)
            .withDataInputs(this.dataInputs.asJava)
            .build()
    }

    def reduced(): ReducedTransaction = {
        this.ctx.newProverBuilder.build.reduce(this.unsigned,0)
    }
}
