package im.paideia.staking.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoId
import im.paideia.staking.contracts.StakeProxy
import im.paideia.util.ConfKeys
import sigmastate.eval.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder

case class StakeProxyBox(
    _ctx: BlockchainContextImpl,
    useContract: StakeProxy,
    daoConfig: DAOConfig, 
    userAddress: String, 
    stakeAmount: Long) extends PaideiaBox {

    ctx = _ctx
    value = 1000000L
    contract = useContract.contract

    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValueBuilder.buildFor(
                Colls.fromArray(Address.create(userAddress).toPropositionBytes())
            )
        )
    }

    override def tokens: List[ErgoToken] = {
        List(
            new ErgoToken(daoConfig.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),stakeAmount)
        )
    }
}
