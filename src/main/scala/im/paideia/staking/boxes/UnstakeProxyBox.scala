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
import im.paideia.staking.StakeRecord
import im.paideia.util.Env

case class UnstakeProxyBox(
    _ctx: BlockchainContextImpl, 
    useContract: UnstakeProxy, 
    daoConfig: DAOConfig, 
    stakeKey: String, 
    userAddress: String, 
    newStakeRecord: StakeRecord) extends PaideiaBox {

    ctx = _ctx
    value = 3000000L
    contract = useContract.contract

    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValueBuilder.buildFor(
                Colls.fromArray(Address.create(userAddress).toPropositionBytes())
            ),
            ErgoValueBuilder.buildFor(
                Colls.fromArray(newStakeRecord.toBytes)
            )
        )
    }

    override def tokens: List[ErgoToken] = {
        List(
            new ErgoToken(stakeKey, 1L),
            new ErgoToken(Env.paideiaTokenId, Env.defaultBotFee)
        )
    }
}
