package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.sdk.ErgoToken
import im.paideia.governance.contracts.DAOOrigin
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.util._
import im.paideia._
import org.ergoplatform.appkit.InputBox
import sigma.Coll
import sigma.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder

case class DAOOriginBox(
  _ctx: BlockchainContextImpl,
  dao: DAO,
  propTokens: Long,
  actionTokens: Long,
  useContract: DAOOrigin,
  _value: Long = 1000000000L
) extends PaideiaBox {

  ctx      = _ctx
  value    = _value
  contract = useContract.contract

  override def registers: List[ErgoValue[_]] = {
    List(
      ErgoValueBuilder.buildFor(Colls.fromArray(ErgoId.create(dao.key).getBytes))
    )
  }

  override def tokens: List[ErgoToken] = {
    List(
      new ErgoToken(Env.daoTokenId, 1L),
      new ErgoToken(
        dao.config.getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid),
        propTokens
      ),
      new ErgoToken(
        dao.config.getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid),
        actionTokens
      )
    )
  }
}

object DAOOriginBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): DAOOriginBox = {
    val contract = DAOOrigin(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
    DAOOriginBox(
      ctx,
      Paideia.getDAO(
        new ErgoId(inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Byte]].toArray)
          .toString()
      ),
      inp.getTokens().get(1).getValue,
      inp.getTokens().get(2).getValue,
      contract,
      inp.getValue()
    )
  }
}
