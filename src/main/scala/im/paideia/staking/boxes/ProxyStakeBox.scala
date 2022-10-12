package im.paideia.staking.boxes

import im.paideia.common.boxes.PaideiaBox
import im.paideia.staking.contracts.ProxyStake
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoId

class ProxyStakeBox(nftId: String, stakeKeyTarget: Address, stakeAmount: Long) extends PaideiaBox {
    override def registers = List[ErgoValue[?]](
        ErgoValue.of(ErgoId.create(nftId).getBytes()),
        ErgoValue.of(stakeKeyTarget.toPropositionBytes()),
        ErgoValue.of(stakeAmount)
    )
}
