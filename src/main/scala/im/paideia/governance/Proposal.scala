package im.paideia.governance

import work.lithos.plasma.collections.PlasmaMap
import org.ergoplatform.sdk.ErgoId
import sigma.data.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.ProxyPlasmaMap
import scorex.db.LDBVersionedStore
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.Digest32
import scorex.crypto.hash.Blake2b256
import im.paideia.util.MempoolPlasmaMap
import im.paideia.Paideia

case class Proposal(
  daoKey: String,
  proposalIndex: Int,
  votes: MempoolPlasmaMap[ErgoId, VoteRecord],
  name: String
)

object Proposal {

  def apply(daoKey: String, proposalIndex: Int, name: String): Proposal = {
    val folder = Paideia.current.proposalDir(daoKey, proposalIndex)
    folder.mkdirs()
    val ldbStore = new LDBVersionedStore(folder, 10)
    val avlStorage = new VersionedLDBAVLStorage(ldbStore)

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
