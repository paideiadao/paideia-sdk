package im.paideia.common.boxes

import im.paideia.util.Env
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.common.contracts.OperatorIncentive
import org.ergoplatform.appkit.ErgoToken

final case class OperatorIncentiveBox(_ctx: BlockchainContextImpl, _value: Long, paideiaTokens: Long, useContract: OperatorIncentive) extends PaideiaBox
{
    ctx = _ctx
    value = _value
    contract = useContract.contract

    override def tokens: List[ErgoToken] = {
        if (paideiaTokens>0L) {
            List(
                new ErgoToken(Env.paideiaTokenId,paideiaTokens)
            )
        } else {
            List[ErgoToken]()
        }
    }
}
