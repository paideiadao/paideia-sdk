package im.paideia.staking.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAOConfig
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.AddStakeProxy

case class AddStakeProxyBox(
    _ctx: BlockchainContextImpl, 
    useContract: AddStakeProxy, 
    daoConfig: DAOConfig, 
    stakeKey: String, 
    userAddress: String, 
    addAmount: Long) extends PaideiaBox {

    ctx = _ctx
    value = 1000000L
    contract = useContract.contract

    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValue.of(
                Address.create(userAddress).toPropositionBytes()
            )
        )
    }

    override def tokens: List[ErgoToken] = {
        List(
            new ErgoToken(stakeKey, 1L),
            new ErgoToken(daoConfig.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),addAmount)
        )
    }
}
