package im.paideia.governance.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.common.boxes.PaideiaBox
import im.paideia.governance.contracts.Vote
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.ErgoId
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.ConfKeys

case class VoteBox(_ctx: BlockchainContextImpl, daoConfig: DAOConfig, voteKey: String, stakeKey: String, releaseTime: Long, useContract: Vote) extends PaideiaBox
{
    ctx = _ctx
    value = 1000000L
    contract = useContract.contract

    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValue.of(releaseTime),
            ErgoValue.of(ErgoId.create(voteKey).getBytes())
        )
    }

    override def tokens: List[ErgoToken] = {
        List(
            new ErgoToken(new ErgoId(daoConfig.getArray[Byte](ConfKeys.im_paideia_dao_vote_tokenid)),1L),
            new ErgoToken(ErgoId.create(stakeKey),1L)
        )
    }
}
