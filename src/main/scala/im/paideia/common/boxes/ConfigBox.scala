package im.paideia.common.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.common.contracts.Config

class ConfigBox(_ctx: BlockchainContextImpl, _configIndex: Long) extends PaideiaBox {
    this.value = 1000000
    this.ctx = _ctx
    this.contract = Config(networkType=ctx.getNetworkType()).contract
    val configIndex = _configIndex
}

object ConfigBox {
    val daoConfigIndex = 0L
    val stakingConfigIndex = 1L
}