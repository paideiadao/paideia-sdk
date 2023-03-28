package im.paideia

import scala.collection.mutable.HashMap
import im.paideia.governance.Proposal

case class DAO(
  key: String,
  config: DAOConfig,
  proposals: HashMap[Int, Proposal] = new HashMap[Int, Proposal]()
) {

  def newProposal: Proposal = {
    proposals(proposals.size) = Proposal(key, proposals.size)
    proposals(proposals.size - 1)
  }
}
