package im.paideia.governance

import io.getblok.getblok_plasma.collections.PlasmaMap
import org.ergoplatform.appkit.ErgoId
import sigmastate.AvlTreeFlags
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.ProxyPlasmaMap
import java.io.File
import scorex.db.LDBVersionedStore
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.Digest32
import scorex.crypto.hash.Blake2b256
import im.paideia.util.MempoolPlasmaMap

case class Proposal(
  daoKey: String,
  proposalIndex: Int,
  votes: MempoolPlasmaMap[ErgoId, VoteRecord]
)

object Proposal {

  def apply(daoKey: String, proposalIndex: Int): Proposal = {
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
      )
    )
  }
}
