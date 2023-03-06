package im.paideia.governance

import io.getblok.getblok_plasma.collections.PlasmaMap
import org.ergoplatform.appkit.ErgoId
import sigmastate.AvlTreeFlags
import io.getblok.getblok_plasma.PlasmaParameters

case class Proposal(votes: PlasmaMap[ErgoId,VoteRecord] = new PlasmaMap[ErgoId,VoteRecord](AvlTreeFlags.AllOperationsAllowed,PlasmaParameters.default)) {

}
