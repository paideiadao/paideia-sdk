package im.paideia.staking.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAOConfig
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.UnstakeProxy
import sigmastate.eval.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder

case class UnstakeProxyBox(
    _ctx: BlockchainContextImpl, 
    useContract: UnstakeProxy, 
    daoConfig: DAOConfig, 
    stakeKey: String, 
    userAddress: String, 
    removeAmount: Long) extends PaideiaBox {

    ctx = _ctx
    value = 1000000L
    contract = useContract.contract

    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValueBuilder.buildFor(
                Colls.fromArray(Address.create(userAddress).toPropositionBytes())
            ),
            ErgoValueBuilder.buildFor(removeAmount)
        )
    }

    override def tokens: List[ErgoToken] = {
        List(
            new ErgoToken(stakeKey, 1L)
        )
    }
}
