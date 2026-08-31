package im.paideia

import im.paideia.util.Util
import scala.collection.mutable.HashMap
import scala.collection.mutable.Map
import sigma.Coll
import org.ergoplatform.sdk.ErgoId
import im.paideia.common.contracts._
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.NetworkType
import im.paideia.staking.contracts._
import work.lithos.plasma.collections.PlasmaMap
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.ByteConversion
import shapeless.Lazy
import scala.reflect.ClassTag
import scala.collection.mutable
import scorex.db.LDBVersionedStore
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.Digest32
import scorex.crypto.hash.Blake2b256
import work.lithos.plasma.collections.LocalPlasmaMap
import scorex.crypto.authds.avltree.batch.BatchAVLProver
import im.paideia.util.MempoolPlasmaMap
import scorex.crypto.authds.ADDigest
import org.ergoplatform.restapi.client.ErgoTransactionInput
import im.paideia.common.events.TransactionEvent
import im.paideia.governance.boxes.ActionUpdateConfigBox
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.common.events.UpdateConfigEvent
import im.paideia.common.contracts.PaideiaContractSignature
import sigma.data.AvlTreeFlags
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType.FilterType
import im.paideia.common.filtering
import im.paideia.governance.boxes.MintBox
import im.paideia.common.boxes.ConfigBox
import sigma.AvlTree

case class DAOConfig(
  val _config: MempoolPlasmaMap[DAOConfigKey, Array[Byte]],
  val daoKey: String
) {

  /** Original-key names are not persisted (and dynamic keys, e.g. the per-proposal
    * im.paideia.contracts.proposal.<bytes> keys, never had one), so keys is tracked by
    * hashedKey instead: it's what's actually stored in the tree and what set() needs to
    * decide insert vs. update. Repopulated from the persisted tree below so a restored
    * DAOConfig doesn't wrongly insert on every set() after a restart.
    */
  var keys: mutable.Set[List[Byte]] = mutable.Set[List[Byte]]()
  if (_config.storage.nonEmpty) {
    keys ++= _config.initiate().toMap.keys.map(_.hashedKey.toList)
  }

  def apply[T](key: String): T = {
    apply[T](DAOConfigKey(key))
  }

  def getLatestDigest: Option[ADDigest] = {
    val potentialBoxes = Paideia.getBox(
      new filtering.FilterNode(
        filtering.FilterType.FTANY,
        List(
          new FilterLeaf[String](
            filtering.FilterType.FTEQ,
            daoKey,
            filtering.CompareField.ASSET,
            0
          ),
          new FilterLeaf[Iterable[Byte]](
            filtering.FilterType.FTEQ,
            ErgoId.create(daoKey).getBytes.toIterable,
            filtering.CompareField.REGISTER,
            1
          )
        )
      )
    )

    try {
      Some(
        ADDigest @@ potentialBoxes
          .filter(b =>
            b.getRegisters().get(0).getValue().isInstanceOf[AvlTree] && _config
              .getMap(
                Some(
                  ADDigest @@ b
                    .getRegisters()
                    .get(0)
                    .getValue()
                    .asInstanceOf[AvlTree]
                    .digest
                    .toArray
                )
              )
              .isDefined
          )(0)
          .getRegisters()
          .get(0)
          .getValue()
          .asInstanceOf[AvlTree]
          .digest
          .toArray
      )
    } catch {
      case e: Exception => None
    }
  }

  def apply[T](key: DAOConfigKey, digestOpt: Option[ADDigest] = None): T = {
    val digest = if (digestOpt.isEmpty) getLatestDigest else digestOpt
    val check  = _config.lookUpWithDigest(key)(digest).response.head.tryOp.get.get
    DAOConfigValueDeserializer.deserialize(check)
  }

  def withDefault[T](
    key: DAOConfigKey,
    default: T,
    digestOpt: Option[ADDigest] = None
  ): T = {
    val check = _config.lookUpWithDigest(key)(digestOpt).response.head.tryOp.get
    if (check.isDefined) {
      DAOConfigValueDeserializer.deserialize(check.get)
    } else {
      default
    }
  }

  def getArray[T](key: DAOConfigKey, digestOpt: Option[ADDigest] = None)(implicit
    tag: ClassTag[T]
  ): Array[T] = {
    apply[Array[Object]](key, digestOpt).map(_.asInstanceOf[T]).toArray
  }

  def set[T](key: DAOConfigKey, value: T, height: Int = 0)(implicit
    enc: DAOConfigValueSerializer[T]
  ) = {
    if (keys.contains(key.hashedKey.toList)) {
      _config.updateWithDigest((key, enc.serialize(value, true, key.readOnly).toArray))(
        Right(height)
      )
    } else {
      keys.add(key.hashedKey.toList)
      _config.insertWithDigest((key, enc.serialize(value, true, key.readOnly).toArray))(
        Right(height)
      )
    }
  }

  def handleUpdateEvent(event: UpdateConfigEvent) = {
    (event.insertEntries ++ event.updatedEntries).foreach(
      (kv: (DAOConfigKey, Array[Byte])) => {
        DAOConfigValueDeserializer.getType(kv._2) match {
          case "PaideiaContractSignature" =>
            Paideia.instantiateContractInstance(
              DAOConfigValueDeserializer[PaideiaContractSignature](kv._2).withDaoKey(
                event.daoKey
              )
            )
          case _: Any =>
        }
      }
    )
    event.digestOrHeight match {
      case Left(value) =>
        val digestAfterRemove =
          if (event.removedKeys.size > 0)
            removeProof(event.removedKeys: _*)(event.digestOrHeight)._2
          else value
        val digestAfterUpdate =
          if (event.updatedEntries.size > 0)
            updateProof(event.updatedEntries: _*)(Left(digestAfterRemove))._2
          else digestAfterRemove
        if (event.insertEntries.size > 0)
          insertProof(event.insertEntries: _*)(Left(digestAfterUpdate))
      case Right(value) =>
        if (event.removedKeys.size > 0)
          removeProof(event.removedKeys: _*)(event.digestOrHeight)
        if (event.updatedEntries.size > 0)
          updateProof(event.updatedEntries: _*)(event.digestOrHeight)
        if (event.insertEntries.size > 0)
          insertProof(event.insertEntries: _*)(event.digestOrHeight)
    }
  }

  def getProof(
    keys: String*
  )(digestOpt: Option[ADDigest]): ErgoValue[Coll[java.lang.Byte]] = {
    getProof(keys.map(DAOConfigKey(_)): _*)(digestOpt)
  }

  def getProof(
    keys: DAOConfigKey*
  )(
    digestOpt: Option[ADDigest]
  )(implicit dummy: DummyImplicit): ErgoValue[Coll[java.lang.Byte]] = {
    val provRes = _config.lookUpWithDigest(keys: _*)(digestOpt)
    provRes.proof.ergoValue.asInstanceOf[ErgoValue[Coll[java.lang.Byte]]]
  }

  def insertProof(operations: (String, Array[Byte])*)(
    digestOrHeight: Either[ADDigest, Int]
  ): (ErgoValue[Coll[java.lang.Byte]], ADDigest) = {
    insertProof(
      operations.map((kv: (String, Array[Byte])) => (DAOConfigKey(kv._1), kv._2)): _*
    )(digestOrHeight)
  }

  def insertProof(
    operations: (DAOConfigKey, Array[Byte])*
  )(
    digestOrHeight: Either[ADDigest, Int]
  )(implicit dummy: DummyImplicit): (ErgoValue[Coll[java.lang.Byte]], ADDigest) = {
    val provRes = _config.insertWithDigest(operations: _*)(digestOrHeight)
    (provRes.proof.ergoValue.asInstanceOf[ErgoValue[Coll[java.lang.Byte]]], provRes.digest)
  }

  def removeProof(
    operations: DAOConfigKey*
  )(
    digestOrHeight: Either[ADDigest, Int]
  )(implicit dummy: DummyImplicit): (ErgoValue[Coll[java.lang.Byte]], ADDigest) = {
    val provRes = _config.deleteWithDigest(operations: _*)(digestOrHeight)
    (provRes.proof.ergoValue.asInstanceOf[ErgoValue[Coll[java.lang.Byte]]], provRes.digest)
  }

  def updateProof(
    operations: (DAOConfigKey, Array[Byte])*
  )(
    digestOrHeight: Either[ADDigest, Int]
  )(implicit dummy: DummyImplicit): (ErgoValue[Coll[java.lang.Byte]], ADDigest) = {
    val provRes = _config.updateWithDigest(operations: _*)(digestOrHeight)
    (provRes.proof.ergoValue.asInstanceOf[ErgoValue[Coll[java.lang.Byte]]], provRes.digest)
  }

}

object DAOConfig {

  def apply(daoKey: String): DAOConfig = {
    val folder = Paideia.current.daoConfigDir(daoKey)
    folder.mkdirs()
    val ldbStore = new LDBVersionedStore(folder, 10)
    val avlStorage = new VersionedLDBAVLStorage(ldbStore)

    new DAOConfig(
      new MempoolPlasmaMap[DAOConfigKey, Array[Byte]](
        avlStorage,
        AvlTreeFlags.AllOperationsAllowed,
        PlasmaParameters.default
      ),
      daoKey
    )
  }

  implicit val convertDAOConfigKey: ByteConversion[DAOConfigKey] =
    new ByteConversion[DAOConfigKey] {
      def convertToBytes(t: DAOConfigKey): Array[Byte] = t.hashedKey

      def convertFromBytes(bytes: Array[Byte]): DAOConfigKey = new DAOConfigKey(bytes)
    }
}
