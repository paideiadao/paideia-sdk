package im.paideia.common.sync

import im.paideia.Paideia
import im.paideia.util.Util
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import sigma.AvlTree
import sigma.Coll

import scala.collection.JavaConverters._

/** One digest comparison between the local (in-memory session) view and what was actually
  * read back from an on-chain box.
  *
  * @param daoKey
  *   \- the DAO the digest belongs to.
  * @param kind
  *   \- what kind of digest this is: `"config"`, `"stake"`, `"participation"` or
  *   `"votes"`.
  * @param detail
  *   \- extra human-readable detail distinguishing checks of the same `kind` for the same
  *   DAO (e.g. `"3:Treasury upgrade"` for a proposal's votes digest); empty for the
  *   per-DAO config/stake/participation digests.
  * @param expected
  *   \- the digest hex the local session expects (from `dao.config._config.digest`,
  *   `TotalStakingState.currentStakingState`'s digests, or `Proposal.votes.digest`).
  * @param onChain
  *   \- the digest hex actually read from the matching on-chain box's register, or `None`
  *   if no matching on-chain box was found at all.
  */
case class DigestCheck(
  daoKey: String,
  kind: String,
  detail: String,
  expected: String,
  onChain: Option[String]
) {
  def ok: Boolean = onChain.contains(expected)
}

/** One confirmed-box-set comparison between the local session and the node, for a single
  * contract instance (identified by its ErgoTree/class/DAO).
  *
  * @param contractClass
  *   \- the contract's simple class name (e.g. `"Config"`, `"StakeState"`,
  *   `"ProposalBasic"`).
  * @param daoKey
  *   \- the DAO this contract instance belongs to.
  * @param missingOnNode
  *   \- box ids the local session considers confirmed-unspent but the node does not
  *   report as unspent (a checkpoint that thinks a spent/nonexistent box is still live).
  * @param extraOnNode
  *   \- box ids the node reports as unspent that the local session doesn't know about (a
  *   checkpoint that's missing boxes the chain actually has).
  */
case class BoxSetCheck(
  contractClass: String,
  daoKey: String,
  missingOnNode: Set[String],
  extraOnNode: Set[String]
) {
  def ok: Boolean = missingOnNode.isEmpty && extraOnNode.isEmpty
}

/** The full result of comparing a session's in-memory state against on-chain reality.
  */
case class VerificationReport(
  digestChecks: Seq[DigestCheck],
  boxSetChecks: Seq[BoxSetCheck]
) {

  def ok: Boolean = digestChecks.forall(_.ok) && boxSetChecks.forall(_.ok)

  /** A short human-readable summary: `"OK: ..."` with the checks performed if every check
    * passed, or one line per failing check otherwise.
    */
  def describe: String = {
    val failedDigests = digestChecks.filterNot(_.ok).map { c =>
      val where = if (c.detail.isEmpty) c.kind else s"${c.kind} ${c.detail}"
      s"[digest] DAO ${c.daoKey} $where: expected ${c.expected}, on-chain " +
        c.onChain.getOrElse("<no matching box>")
    }
    val failedBoxSets = boxSetChecks.filterNot(_.ok).map { c =>
      s"[boxes] ${c.contractClass} DAO ${c.daoKey}: " +
        s"missingOnNode=[${c.missingOnNode.mkString(",")}] " +
        s"extraOnNode=[${c.extraOnNode.mkString(",")}]"
    }
    val failures = failedDigests ++ failedBoxSets
    if (failures.isEmpty)
      s"OK: ${digestChecks.size} digest check(s), ${boxSetChecks.size} box-set check(s) all passed"
    else
      s"FAILED: ${failures.size} of ${digestChecks.size + boxSetChecks.size} check(s):\n" +
        failures.mkString("\n")
  }
}

/** Compares a Paideia session's in-memory state (a checkpoint restored from an untrusted
  * source via [[im.paideia.PaideiaSession.restoreState]], then replayed to the chain tip by
  * [[ChainSyncer]]) against what's actually unspent on-chain right now, fetched through an
  * [[IndexedNodeClient]]. Any mismatch - a digest that doesn't match, a box the session
  * thinks is unspent that the node doesn't, or vice versa - means the checkpoint (or the
  * replay on top of it) was bad.
  *
  * Deliberately split into three layers so the actual comparison logic
  * ([[compare]]) is unit-testable with synthetic data, no live session or node required:
  *   - the local snapshot ([[localSnapshot]]) reads `Paideia.current`'s registries;
  *   - the on-chain fetch ([[verify]]) reads an [[IndexedNodeClient]];
  *   - [[compare]] itself does no I/O at all.
  */
object ChainStateVerifier {

  private val ConfigClass        = "Config"
  private val StakeStateClass    = "StakeState"
  private val ProposalBasicClass = "ProposalBasic"

  /** One live contract instance, as recorded locally: enough to look up its unspent boxes
    * on-chain (by ErgoTree) and compare confirmed-box-id sets.
    *
    * Mirrors the walk `PaideiaSession.persistState` does over `actorList.values.flatMap(
    * _.contractInstances.values)` - see there for why every live instance is walked
    * rather than the DAO config tree (proxy contracts, direct construction, the
    * longLivingKey re-instantiation path can all produce instances a config-tree walk
    * would miss).
    */
  private[sync] case class LocalContractInstance(
    ergoTreeHex: String,
    contractClass: String,
    daoKey: String,
    confirmedBoxIds: Set[String]
  )

  /** One digest the local session expects to find on-chain.
    *
    * @param matchKey
    *   \- extra key used only to pick the right on-chain box out of several candidates
    *   with the same `(daoKey, kind)` (the proposal index, for `"votes"`); unused (empty)
    *   for `"config"`/`"stake"`/`"participation"`, which - in this codebase - have at
    *   most one live contract instance per DAO.
    */
  private[sync] case class LocalDigest(
    daoKey: String,
    kind: String,
    detail: String,
    matchKey: String,
    expectedHex: String
  )

  private[sync] case class LocalSnapshot(
    contractInstances: Seq[LocalContractInstance],
    digests: Seq[LocalDigest]
  )

  /** Gathers a [[LocalSnapshot]] from `Paideia.current`'s live registries: every contract
    * instance across every registered actor (see [[LocalContractInstance]]'s scaladoc for
    * why that's the whole `actorList`, not a config-tree walk), plus the digests every
    * checkpoint round-trips through `persistState`/`restoreState` - the per-DAO config
    * digest, the per-DAO current staking digests (only for DAOs with a staking state
    * registered - see `PaideiaSession.stakingStates`), and every proposal's votes digest.
    * Hex-encoded the same way `persistState` does (`Util.bytes2hex`), so the expected
    * values here match a checkpoint's `state.json` byte for byte.
    */
  private[sync] def localSnapshot(): LocalSnapshot = {
    val contractInstances =
      Paideia._actorList.values
        .flatMap(_.contractInstances.values)
        .map { instance =>
          LocalContractInstance(
            ergoTreeHex     = instance.ergoTreeHex,
            contractClass   = instance.getClass.getSimpleName,
            daoKey          = instance.contractSignature.daoKey,
            confirmedBoxIds = instance.utxos.toSet intersect instance.boxes.keySet.toSet
          )
        }
        .toSeq

    val configDigests = Paideia._daoMap.values.map { dao =>
      LocalDigest(dao.key, "config", "", "", Util.bytes2hex(dao.config._config.digest))
    }.toSeq

    val stakingDigests = Paideia._daoMap.keys.flatMap { daoKey =>
      Paideia.current.stakingStates.get(daoKey).toSeq.flatMap { tss =>
        Seq(
          LocalDigest(
            daoKey,
            "stake",
            "",
            "",
            Util.bytes2hex(tss.currentStakingState.stakeRecords.digest)
          ),
          LocalDigest(
            daoKey,
            "participation",
            "",
            "",
            Util.bytes2hex(tss.currentStakingState.participationRecords.digest)
          )
        )
      }
    }.toSeq

    val proposalDigests = Paideia._daoMap.values.flatMap { dao =>
      dao.proposals.map { case (index, proposal) =>
        LocalDigest(
          dao.key,
          "votes",
          s"$index:${proposal.name}",
          index.toString,
          Util.bytes2hex(proposal.votes.digest)
        )
      }
    }.toSeq

    LocalSnapshot(
      contractInstances,
      configDigests ++ stakingDigests ++ proposalDigests
    )
  }

  private def toInputBox(output: ErgoTransactionOutput): InputBox = new InputBoxImpl(output)

  /** The config box's daoKey NFT is token 0 - see `ConfigBox.fromInputBox`. */
  private def configBoxDaoKey(box: InputBox): Option[String] =
    box.getTokens().asScala.headOption.map(_.id.toString())

  private def configDigestHex(box: InputBox): String =
    Util.bytes2hex(
      box.getRegisters().get(0).getValue().asInstanceOf[AvlTree].digest.toArray
    )

  /** Register index 0 is `Coll[AvlTree]`: (0) = stake tree, (1) = participation tree - see
    * `StakeStateBox.fromInputBox`, mirrored here down to the `.map(_.digest.toArray)`
    * shape.
    */
  private def stakeStateTreeDigestHex(box: InputBox, index: Int): String = {
    val stateTrees =
      box.getRegisters().get(0).getValue().asInstanceOf[Coll[AvlTree]].map(_.digest.toArray)
    Util.bytes2hex(stateTrees(index))
  }

  /** Register index 0 is `Coll[Int]`, (0) = proposal index - see
    * `ProposalBasicBox.fromInputBox`.
    */
  private def proposalIndexOf(box: InputBox): Int = {
    val ints =
      box.getRegisters().get(0).getValue().asInstanceOf[Coll[Int]].map(_.toInt).toArray
    ints(0)
  }

  /** Register index 2 is the votes `AvlTree` - see `ProposalBasicBox.fromInputBox`. */
  private def proposalVotesDigestHex(box: InputBox): String =
    Util.bytes2hex(
      box.getRegisters().get(2).getValue().asInstanceOf[AvlTree].digest.toArray
    )

  /** Pure comparison of a [[LocalSnapshot]] against on-chain boxes already fetched per
    * ErgoTree (as [[verify]] does via [[IndexedNodeClient.unspentBoxesByErgoTree]]). Does
    * no I/O - every box it looks at was fetched by the caller - so it's exercised directly
    * in tests against synthetic snapshots and fixture `ErgoTransactionOutput`s.
    *
    * @param local
    *   \- the local session's expected state.
    * @param onChain
    *   \- every distinct ErgoTree's currently-unspent boxes, keyed by hex-encoded
    *   ErgoTree; an ErgoTree with no entry (or an empty list) is treated as having no
    *   unspent boxes on-chain.
    * @return
    *   one [[BoxSetCheck]] per local contract instance, and one [[DigestCheck]] per local
    *   digest.
    */
  private[sync] def compare(
    local: LocalSnapshot,
    onChain: Map[String, List[ErgoTransactionOutput]]
  ): VerificationReport = {

    def boxesFor(ergoTreeHex: String): List[ErgoTransactionOutput] =
      onChain.getOrElse(ergoTreeHex, Nil)

    val boxSetChecks = local.contractInstances.map { instance =>
      val onChainIds = boxesFor(instance.ergoTreeHex).map(_.getBoxId()).toSet
      BoxSetCheck(
        instance.contractClass,
        instance.daoKey,
        missingOnNode = instance.confirmedBoxIds -- onChainIds,
        extraOnNode   = onChainIds -- instance.confirmedBoxIds
      )
    }

    // Every on-chain box sitting behind an ErgoTree used by any local contract instance
    // of `contractClass` for `daoKey` - a digest's matching box can live on any of them
    // (there's normally exactly one, but nothing here assumes that).
    def candidateBoxes(daoKey: String, contractClass: String): Seq[InputBox] =
      local.contractInstances
        .filter(i => i.daoKey == daoKey && i.contractClass == contractClass)
        .flatMap(i => boxesFor(i.ergoTreeHex))
        .map(toInputBox)

    val digestChecks = local.digests.map { digest =>
      val onChainHex: Option[String] = digest.kind match {
        case "config" =>
          candidateBoxes(digest.daoKey, ConfigClass)
            .find(box => configBoxDaoKey(box).contains(digest.daoKey))
            .map(configDigestHex)
        case "stake" =>
          candidateBoxes(digest.daoKey, StakeStateClass).headOption
            .map(stakeStateTreeDigestHex(_, 0))
        case "participation" =>
          candidateBoxes(digest.daoKey, StakeStateClass).headOption
            .map(stakeStateTreeDigestHex(_, 1))
        case "votes" =>
          val wantIndex = digest.matchKey.toInt
          candidateBoxes(digest.daoKey, ProposalBasicClass)
            .find(box => proposalIndexOf(box) == wantIndex)
            .map(proposalVotesDigestHex)
        case other =>
          throw new IllegalArgumentException(s"Unknown digest kind: $other")
      }
      DigestCheck(digest.daoKey, digest.kind, digest.detail, digest.expectedHex, onChainHex)
    }

    VerificationReport(digestChecks, boxSetChecks)
  }

  /** Verifies the current session (`Paideia.current`) against on-chain reality through
    * `client`: gathers the local snapshot, fetches unspent boxes for every ErgoTree that
    * either has a nonempty confirmed set locally or belongs to a Config/StakeState/
    * ProposalBasic contract instance (so a digest check always has something to look for,
    * even when the local session thinks a contract has zero confirmed boxes - itself a
    * mismatch worth reporting via that box set's `extraOnNode`), and compares.
    *
    * @param client
    *   \- reaches the node's indexed `blockchain` endpoints.
    * @return
    *   the full comparison report; see [[VerificationReport.ok]] for the pass/fail
    *   summary.
    */
  def verify(client: IndexedNodeClient): VerificationReport = {
    val local = localSnapshot()

    val digestBackedClasses = Set(ConfigClass, StakeStateClass, ProposalBasicClass)
    val ergoTreesToFetch = local.contractInstances
      .filter(i => i.confirmedBoxIds.nonEmpty || digestBackedClasses.contains(i.contractClass))
      .map(_.ergoTreeHex)
      .distinct

    val onChain = ergoTreesToFetch.map(t => t -> client.unspentBoxesByErgoTree(t)).toMap

    compare(local, onChain)
  }
}
