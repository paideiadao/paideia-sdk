package im.paideia.governance.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfigKey
import im.paideia.common.boxes.PaideiaBox
import im.paideia.DAO
import im.paideia.governance.contracts.ActionUpdateConfig
import org.ergoplatform.sdk.ErgoToken
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoValue
import sigma.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import im.paideia.DAOConfigValueSerializer
import org.ergoplatform.appkit.InputBox
import scorex.crypto.hash.Blake2b256
import sigma.Coll
import im.paideia.Paideia
import scala.collection.JavaConverters._

final case class ActionUpdateConfigBox(
  _ctx: BlockchainContextImpl,
  useContract: ActionUpdateConfig,
  dao: DAO,
  proposalId: Int,
  optionId: Int,
  activationTime: Long,
  remove: List[DAOConfigKey],
  update: List[(DAOConfigKey, Array[Byte])],
  insert: List[(DAOConfigKey, Array[Byte])]
) extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract

  override def tokens: List[ErgoToken] = List(
    new ErgoToken(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid), 1L)
  )

  override def registers: List[ErgoValue[_]] = List(
    ErgoValueBuilder.buildFor(
      Colls.fromArray(Array(proposalId.toLong, optionId.toLong, 0L, activationTime, 0L))
    ),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        remove.map(dck => Colls.fromArray(dck.hashedKey)).toArray
      )
    ),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        update
          .map(dcv => (Colls.fromArray(dcv._1.hashedKey), Colls.fromArray(dcv._2)))
          .toArray
      )
    ),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        insert
          .map(dcv => (Colls.fromArray(dcv._1.hashedKey), Colls.fromArray(dcv._2)))
          .toArray
      )
    )
  )
}

object ActionUpdateConfigBox {

  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ActionUpdateConfigBox = {
    val contract = ActionUpdateConfig
      .contractInstances(Blake2b256(inp.getErgoTree().bytes).array.toList)
      .asInstanceOf[ActionUpdateConfig]
    val params = inp.getRegisters().get(0).getValue.asInstanceOf[Coll[Long]]
    ActionUpdateConfigBox(
      ctx,
      contract,
      Paideia.getDAO(contract.contractSignature.daoKey),
      params(0).toInt,
      params(1).toInt,
      params(3),
      inp
        .getRegisters()
        .get(1)
        .getValue()
        .asInstanceOf[Coll[Coll[Byte]]]
        .toArray
        .map(collB => new DAOConfigKey(_hashedKey = collB.toArray))
        .toList,
      inp
        .getRegisters()
        .get(2)
        .getValue()
        .asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]]
        .toArray
        .map(collColl =>
          (new DAOConfigKey(_hashedKey = collColl._1.toArray), collColl._2.toArray)
        )
        .toList,
      inp
        .getRegisters()
        .get(3)
        .getValue()
        .asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]]
        .toArray
        .map(collColl =>
          (new DAOConfigKey(_hashedKey = collColl._1.toArray), collColl._2.toArray)
        )
        .toList
    )
  }
}
