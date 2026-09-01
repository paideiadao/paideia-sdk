package im.paideia.common.sync

import org.scalatest.funsuite.AnyFunSuite
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import org.ergoplatform.restapi.client.Asset
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import org.ergoplatform.restapi.client.Registers
import sigma.Colls
import sigma.AvlTree
import sigma.data.AvlTreeData
import sigma.data.AvlTreeFlags
import sigma.data.CAND
import sigma.ast.ErgoTree
import sigma.ast.SigmaPropConstant
import im.paideia.util.Util
import im.paideia.common.sync.ChainStateVerifier._

import scala.collection.JavaConverters._

/** Covers `ChainStateVerifier.compare` - the pure layer, exercised directly against
  * synthetic [[LocalSnapshot]]s and fixture `ErgoTransactionOutput`s, with no live session
  * or node involved. Registers are real sigma-serialized values (built the same way
  * `ConfigBox`/`StakeStateBox`/`ProposalBasicBox`'s own `registers` do, via
  * `ErgoValueBuilder`/`ErgoValue.of`), so `compare`'s `new InputBoxImpl(output)` +
  * `getRegisters()` round-trip exactly as it would against a real node response - not
  * hand-written hex.
  *
  * `localSnapshot`/`verify` (the I/O-touching layers, reading `Paideia.current` and an
  * [[IndexedNodeClient]] respectively) are intentionally not covered here - that's exactly
  * the split [[ChainStateVerifier]]'s scaladoc describes, and exercising them would need a
  * fully bootstrapped session/DAO.
  */
class ChainStateVerifierSuite extends AnyFunSuite {

  // A real P2PK ErgoTree - `compare` converts every on-chain box via `new
  // InputBoxImpl(output)` (as `PaideiaSession.restoreState` does), which eagerly parses
  // `ergoTree`/`tokens`/`registers` into a genuine `ErgoBox`, so these need to be
  // well-formed rather than placeholder strings wherever a digest check is exercised (box
  // set checks alone never call into InputBoxImpl - see the "box set" tests below, which
  // use plain placeholder ErgoTree strings).
  private val p2pkTreeHex: String =
    Address
      .create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v")
      .toErgoContract()
      .getErgoTree()
      .bytesHex

  /** A second/third distinct-but-genuinely-valid ErgoTree, derived from the same P2PK
    * public key wrapped in an n-way (trivial, always-satisfiable-by-that-key) AND - a
    * different serialized tree from `p2pkTreeHex` without needing a second real keypair.
    */
  private def wrappedTreeHex(copies: Int): String = {
    val pk   = Address.create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v").getSigmaBoolean()
    val prop = CAND(Seq.fill(copies)(pk))
    ErgoTree.fromProposition(SigmaPropConstant(prop)).bytesHex
  }

  private def fill33(b: Byte): Array[Byte] = Array.fill[Byte](33)(b)
  private def digestHex(b: Byte): String   = Util.bytes2hex(fill33(b))
  private def hexId(b: Byte): String       = Util.bytes2hex(Array.fill[Byte](32)(b))

  /** A digest check's box lookup converts every candidate on-chain box via `new
    * InputBoxImpl(output)`, which Base16-decodes `boxId` eagerly - so every box used in a
    * digest-check test needs a real hex id, unlike the box-set-only test below (which
    * never touches InputBoxImpl and can use arbitrary placeholder ids).
    */
  private def boxIdHex(b: Byte): String = Util.bytes2hex(Array.fill[Byte](32)(b))

  private def avlTreeValue(b: Byte): AvlTree =
    ErgoValue
      .of(AvlTreeData(Colls.fromArray(fill33(b)), AvlTreeFlags.AllOperationsAllowed, 32, None))
      .getValue

  private def avlRegisterHex(b: Byte): String =
    ErgoValue
      .of(AvlTreeData(Colls.fromArray(fill33(b)), AvlTreeFlags.AllOperationsAllowed, 32, None))
      .toHex()

  private def registersOf(hexValues: String*): Registers = {
    val r = new Registers()
    hexValues.zipWithIndex.foreach { case (hex, i) => r.put(s"R${4 + i}", hex) }
    r
  }

  private def outputBox(
    boxId: String,
    ergoTreeHex: String,
    tokens: List[(String, Long)] = Nil,
    registers: Registers         = new Registers()
  ): ErgoTransactionOutput =
    new ErgoTransactionOutput()
      .boxId(boxId)
      .value(1000000L)
      .ergoTree(ergoTreeHex)
      .creationHeight(100)
      .assets(tokens.map { case (id, amt) => new Asset().tokenId(id).amount(amt) }.asJava)
      .additionalRegisters(registers)
      .transactionId(boxId)
      .index(0)

  /** R4 = config AvlTree, token 0 = daoKey NFT - see `ConfigBox.fromInputBox`. */
  private def configBox(
    boxId: String,
    ergoTreeHex: String,
    daoKey: String,
    configDigestFill: Byte
  ): ErgoTransactionOutput =
    outputBox(
      boxId,
      ergoTreeHex,
      tokens    = List((daoKey, 1L)),
      registers = registersOf(avlRegisterHex(configDigestFill))
    )

  /** R4 = `Coll[AvlTree]`(stake, participation) - see `StakeStateBox.fromInputBox`. */
  private def stakeStateBox(
    boxId: String,
    ergoTreeHex: String,
    stakeFill: Byte,
    participationFill: Byte
  ): ErgoTransactionOutput = {
    val r4 =
      ErgoValueBuilder.buildFor(Colls.fromArray(Array(avlTreeValue(stakeFill), avlTreeValue(participationFill))))
    outputBox(boxId, ergoTreeHex, registers = registersOf(r4.toHex()))
  }

  /** R4 = `Coll[Int]`(index, passed), R5 = filler `Coll[Long]`, R6 = votes AvlTree, R7 =
    * filler `Coll[Byte]` - the full 4-register shape from `ProposalBasicBox.fromInputBox`,
    * even though `compare` only reads R4 and R6, so those two land at register indices 0
    * and 2 exactly as `ProposalBasicBox.fromInputBox` expects.
    */
  private def proposalBox(
    boxId: String,
    ergoTreeHex: String,
    index: Int,
    votesFill: Byte
  ): ErgoTransactionOutput = {
    val r4 = ErgoValueBuilder.buildFor(Colls.fromArray(Array(index, -1)))
    val r5 = ErgoValueBuilder.buildFor(Colls.fromArray(Array[Long](0L, 0L)))
    val r7 = ErgoValueBuilder.buildFor(Colls.fromArray(Array[Byte]()))
    outputBox(
      boxId,
      ergoTreeHex,
      registers = registersOf(r4.toHex(), r5.toHex(), avlRegisterHex(votesFill), r7.toHex())
    )
  }

  test(
    "compare: matching config/staking/proposal digests and matching box sets produce an ok report"
  ) {
    val daoKey       = hexId(1)
    val configTree   = p2pkTreeHex
    val stakeTree    = wrappedTreeHex(2)
    val proposalTree = wrappedTreeHex(3)

    val cfgBoxId  = boxIdHex(0x10.toByte)
    val stakeBoxId = boxIdHex(0x11.toByte)
    val propBoxId = boxIdHex(0x12.toByte)
    val local = LocalSnapshot(
      contractInstances = Seq(
        LocalContractInstance(configTree, "Config", daoKey, Set(cfgBoxId)),
        LocalContractInstance(stakeTree, "StakeState", daoKey, Set(stakeBoxId)),
        LocalContractInstance(proposalTree, "ProposalBasic", daoKey, Set(propBoxId))
      ),
      digests = Seq(
        LocalDigest(daoKey, "config", "", "", digestHex(0xAA.toByte)),
        LocalDigest(daoKey, "stake", "", "", digestHex(0xBB.toByte)),
        LocalDigest(daoKey, "participation", "", "", digestHex(0xCC.toByte)),
        LocalDigest(daoKey, "votes", "0:Treasury upgrade", "0", digestHex(0xDD.toByte))
      )
    )
    val onChain = Map(
      configTree   -> List(configBox(cfgBoxId, configTree, daoKey, 0xAA.toByte)),
      stakeTree    -> List(stakeStateBox(stakeBoxId, stakeTree, 0xBB.toByte, 0xCC.toByte)),
      proposalTree -> List(proposalBox(propBoxId, proposalTree, 0, 0xDD.toByte))
    )

    val report = ChainStateVerifier.compare(local, onChain)

    assert(report.digestChecks.size == 4)
    assert(report.boxSetChecks.size == 3)
    assert(report.ok)
    assert(report.describe.startsWith("OK:"))
  }

  test("compare: a digest mismatch fails that check and the overall report") {
    val daoKey = hexId(2)
    val tree   = p2pkTreeHex
    val cfgBoxId = boxIdHex(0x20.toByte)
    val local = LocalSnapshot(
      contractInstances = Seq(LocalContractInstance(tree, "Config", daoKey, Set(cfgBoxId))),
      digests           = Seq(LocalDigest(daoKey, "config", "", "", digestHex(0xAA.toByte)))
    )
    val onChain = Map(tree -> List(configBox(cfgBoxId, tree, daoKey, 0xFF.toByte)))

    val report = ChainStateVerifier.compare(local, onChain)

    assert(report.digestChecks.size == 1)
    val check = report.digestChecks.head
    assert(!check.ok)
    assert(check.expected == digestHex(0xAA.toByte))
    assert(check.onChain.contains(digestHex(0xFF.toByte)))
    assert(!report.ok)
    assert(report.describe.startsWith("FAILED:"))
  }

  test("compare: no matching on-chain box for a digest reports onChain = None") {
    val daoKey = hexId(3)
    val tree   = p2pkTreeHex
    val local = LocalSnapshot(
      contractInstances = Seq(LocalContractInstance(tree, "Config", daoKey, Set.empty)),
      digests           = Seq(LocalDigest(daoKey, "config", "", "", digestHex(0xAA.toByte)))
    )
    // No box at all behind the ErgoTree.
    val reportNoBox = ChainStateVerifier.compare(local, Map.empty)
    assert(reportNoBox.digestChecks.head.onChain.isEmpty)
    assert(!reportNoBox.ok)

    // A box exists at the tree, but its token 0 doesn't match daoKey (a differently-DAO'd
    // Config box sitting behind an ErgoTree this test otherwise reuses).
    val onChainWrongDao =
      Map(tree -> List(configBox(boxIdHex(0x30.toByte), tree, hexId(9), 0xAA.toByte)))
    val reportWrongDao  = ChainStateVerifier.compare(local, onChainWrongDao)
    assert(reportWrongDao.digestChecks.head.onChain.isEmpty)
    assert(!reportWrongDao.ok)
  }

  test("compare: box set mismatches report missing and extra box ids independently per contract") {
    val daoKey = hexId(4)
    // Box-set comparison never constructs InputBoxImpl (it only reads getBoxId()), so a
    // placeholder ErgoTree string is fine here - no real sigma-serialized tree needed.
    val configTree = "config-tree-placeholder"
    val stakeTree  = "stake-tree-placeholder"

    val local = LocalSnapshot(
      contractInstances = Seq(
        LocalContractInstance(configTree, "Config", daoKey, Set("a", "b")),
        LocalContractInstance(stakeTree, "StakeState", daoKey, Set("x"))
      ),
      digests = Seq.empty
    )
    val onChain = Map(
      configTree -> List(outputBox("b", configTree), outputBox("c", configTree)),
      stakeTree  -> List.empty[ErgoTransactionOutput]
    )

    val report = ChainStateVerifier.compare(local, onChain)

    assert(report.boxSetChecks.size == 2)
    val configCheck = report.boxSetChecks.find(_.contractClass == "Config").get
    assert(configCheck.missingOnNode == Set("a"))
    assert(configCheck.extraOnNode == Set("c"))
    assert(!configCheck.ok)

    val stakeCheck = report.boxSetChecks.find(_.contractClass == "StakeState").get
    assert(stakeCheck.missingOnNode == Set("x"))
    assert(stakeCheck.extraOnNode == Set.empty)
    assert(!stakeCheck.ok)

    assert(!report.ok)
  }

  test("compare: a proposal's votes digest is matched to its on-chain box by proposal index, not list order") {
    val daoKey = hexId(5)
    val tree   = p2pkTreeHex
    val prop0BoxId = boxIdHex(0x40.toByte)
    val prop1BoxId = boxIdHex(0x41.toByte)

    val local = LocalSnapshot(
      contractInstances =
        Seq(LocalContractInstance(tree, "ProposalBasic", daoKey, Set(prop0BoxId, prop1BoxId))),
      digests = Seq(
        LocalDigest(daoKey, "votes", "0:First", "0", digestHex(0xD0.toByte)),
        LocalDigest(daoKey, "votes", "1:Second", "1", digestHex(0xD1.toByte))
      )
    )
    // On-chain list order is deliberately reversed relative to proposal index.
    val onChain = Map(
      tree -> List(
        proposalBox(prop1BoxId, tree, index = 1, votesFill = 0xD1.toByte),
        proposalBox(prop0BoxId, tree, index = 0, votesFill = 0xD0.toByte)
      )
    )

    val report = ChainStateVerifier.compare(local, onChain)

    assert(report.digestChecks.size == 2)
    val check0 = report.digestChecks.find(_.detail.startsWith("0:")).get
    val check1 = report.digestChecks.find(_.detail.startsWith("1:")).get
    assert(check0.onChain.contains(digestHex(0xD0.toByte)))
    assert(check1.onChain.contains(digestHex(0xD1.toByte)))
    assert(report.ok)
  }

  test(
    "compare: an on-chain box the contract would not track (accepts false) is not extraOnNode, " +
      "but missingOnNode is never filtered"
  ) {
    val daoKey = hexId(6)
    val tree   = "config-tree-placeholder"

    // Local state tracks "a"; on-chain also has "dust" (a box the contract's validateBox
    // would reject - e.g. a tokenless box someone parked at the contract address).
    val local = LocalSnapshot(
      contractInstances = Seq(LocalContractInstance(tree, "Config", daoKey, Set("a", "gone"))),
      digests           = Seq.empty
    )
    val onChain = Map(
      tree -> List(outputBox("a", tree), outputBox("dust", tree))
    )
    val accepts: (String, ErgoTransactionOutput) => Boolean =
      (_, out) => out.getBoxId() != "dust"

    val report = ChainStateVerifier.compare(local, onChain, accepts)

    val check = report.boxSetChecks.head
    // "dust" is excluded from extraOnNode by the accepts filter...
    assert(check.extraOnNode == Set.empty)
    // ...but a locally-tracked box that's gone from the node still fails, unfiltered.
    assert(check.missingOnNode == Set("gone"))
    assert(!report.ok)

    // Without the filter, the dust box is a (false-positive) extra.
    val unfiltered = ChainStateVerifier.compare(local, onChain)
    assert(unfiltered.boxSetChecks.head.extraOnNode == Set("dust"))
  }

  test(
    "compare: extraOnNode fails verification only for digest-backed contract classes; " +
      "elsewhere it's a warning, while missingOnNode always fails"
  ) {
    val daoKey = hexId(7)
    val tree   = "createdao-tree-placeholder"

    // CreateDAO is lazily instantiated (see BoxSetCheck.enforceExtras) - an on-chain box
    // predating the instance is expected, not a verification failure.
    val extrasOnly = LocalSnapshot(
      contractInstances = Seq(LocalContractInstance(tree, "CreateDAO", daoKey, Set("a"))),
      digests           = Seq.empty
    )
    val onChain = Map(tree -> List(outputBox("a", tree), outputBox("pre-instance", tree)))

    val report = ChainStateVerifier.compare(extrasOnly, onChain)
    val check  = report.boxSetChecks.head
    assert(check.extraOnNode == Set("pre-instance"))
    assert(check.ok)
    assert(report.ok)
    assert(report.describe.contains("[boxes][warn]"))
    assert(report.describe.contains("pre-instance"))

    // The same extra on a digest-backed class still fails.
    val enforced = ChainStateVerifier.compare(
      extrasOnly.copy(contractInstances =
        Seq(LocalContractInstance(tree, "ProposalBasic", daoKey, Set("a")))
      ),
      onChain
    )
    assert(!enforced.ok)

    // missingOnNode fails even for a lazily-instantiated class.
    val missing = ChainStateVerifier.compare(
      LocalSnapshot(
        Seq(LocalContractInstance(tree, "CreateDAO", daoKey, Set("a", "gone"))),
        Seq.empty
      ),
      Map(tree -> List(outputBox("a", tree)))
    )
    assert(!missing.ok)
    assert(missing.boxSetChecks.head.missingOnNode == Set("gone"))
  }
}
