package im.paideia.governance

import work.lithos.plasma.collections.PlasmaMap
import org.ergoplatform.sdk.ErgoId
import sigma.data.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.ProxyPlasmaMap
import java.io.File
import scorex.db.LDBVersionedStore
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.Digest32
import scorex.crypto.hash.Blake2b256
import im.paideia.util.MempoolPlasmaMap

case class Proposal(
  daoKey: String,
  proposalIndex: Int,
  votes: MempoolPlasmaMap[ErgoId, VoteRecord],
  name: String
)

object Proposal {

  def apply(daoKey: String, proposalIndex: Int, name: String): Proposal = {
    val folder = new File("./proposals/" ++ daoKey ++ "/" ++ proposalIndex.toString())
    folder.mkdirs()
    val ldbStore = new LDBVersionedStore(folder, 10)
    val avlStorage = new VersionedLDBAVLStorage[Digest32](
      ldbStore,
      PlasmaParameters.default.toNodeParams
    )(Blake2b256)

    new Proposal(
      daoKey,
      proposalIndex,
      new MempoolPlasmaMap[ErgoId, VoteRecord](
        avlStorage,
        AvlTreeFlags.AllOperationsAllowed,
        PlasmaParameters.default
      ),
      name
    )
  }
}
