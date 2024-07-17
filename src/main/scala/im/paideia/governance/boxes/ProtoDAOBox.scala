package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import im.paideia.util.Env
import org.ergoplatform.sdk.ErgoToken
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAO
import im.paideia.Paideia
import org.ergoplatform.sdk.ErgoId
import im.paideia.util.ConfKeys
import scorex.crypto.hash.Blake2b256
import org.ergoplatform.appkit.InputBox
import sigma.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scorex.crypto.authds.ADDigest
import sigma.AvlTree
import sigma.Coll

case class ProtoDAOBox(
  _ctx: BlockchainContextImpl,
  dao: DAO,
  stakePool: Long,
  useContract: ProtoDAO,
  _value: Long                = 1000000L,
  digestOpt: Option[ADDigest] = None
) extends PaideiaBox {
  ctx      = _ctx
  contract = useContract.contract

  value = _value

  override def registers: List[ErgoValue[_]] = {
    List(
      dao.config._config.ergoValue(digestOpt),
      ErgoValueBuilder.buildFor(Colls.fromArray(ErgoId.create(dao.key).getBytes))
    )
  }

  override def tokens: List[ErgoToken] = {
    List(
      new ErgoToken(Env.daoTokenId, 1L),
      new ErgoToken(
        dao.config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid, digestOpt),
        stakePool + 1L
      )
    )
  }
}

object ProtoDAOBox {

  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ProtoDAOBox = {
    val contract = ProtoDAO
      .contractInstances(Blake2b256(inp.getErgoTree.bytes).array.toList)
      .asInstanceOf[ProtoDAO]
    val digest =
      ADDigest @@ inp
        .getRegisters()
        .get(0)
        .getValue()
        .asInstanceOf[AvlTree]
        .digest
        .toArray
    val daoKey = new ErgoId(
      inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Byte]].toArray
    ).toString()
    ProtoDAOBox(
      ctx,
      Paideia.getDAO(daoKey),
      if (inp.getTokens().size > 1) inp.getTokens().get(1).getValue - 1L else 0L,
      contract,
      inp.getValue(),
      Some(digest)
    )
  }
}
