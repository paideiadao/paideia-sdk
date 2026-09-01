package im.paideia.cli

import com.google.gson.JsonObject
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import im.paideia.Paideia
import im.paideia.PaideiaSession
import im.paideia.app.ActionView
import im.paideia.app.DaoSummary
import im.paideia.app.Eip12UnsignedTx
import im.paideia.app.ProposalDetail
import im.paideia.app.ProposalSummary
import im.paideia.app.ReadModels
import im.paideia.app.SendFundsActionView
import im.paideia.app.StakeInfo
import im.paideia.app.StateLifecycle
import im.paideia.app.UpdateConfigActionView
import im.paideia.app.UserBoxSelector
import im.paideia.app.UserTransactions
import im.paideia.common.sync.IndexedNodeClient
import im.paideia.common.sync.NodeBlockSource
import im.paideia.common.transactions.PaideiaTransaction
import im.paideia.staking.StakeRecord
import im.paideia.util.Env
import im.paideia.util.PaideiaEnv
import org.ergoplatform.appkit.UnsignedTransaction
import org.ergoplatform.appkit.impl.BlockchainContextBuilderImpl
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.NodeDataSourceImpl
import org.ergoplatform.restapi.client.ApiClient

import java.io.File
import java.net.NetworkInterface
import java.time.Instant
import java.util.Base64
import scala.collection.JavaConverters._
import scala.util.Try

/** `paideia` - the v1 CLI shell described in `PHASE4-CLI-DESIGN.md` (W3): "a guaranteed
  * surface for any user to interact with Paideia" needing only this JAR, a wallet (not
  * yet wired - that's W4) and an Ergo node. Every command implies a `sync` first: local
  * state is always brought up to date and verified (`StateLifecycle.bringUpToDate`)
  * against the live node before a query runs, so a read command's output is never stale
  * by more than this run's replay.
  *
  * Usage: see [[ArgParser.usage]].
  *
  * Progress/log output goes to stderr; command output goes to stdout - so `paideia dao
  * list > daos.txt` captures only the data, and progress is still visible on the
  * terminal. A bad argument, an unknown DAO/proposal, or a fatal verification failure
  * (`StateLifecycle` giving up - see there) prints its message to stderr and exits
  * non-zero.
  */
object Main {

  def main(args: Array[String]): Unit =
    ArgParser.parse(args) match {
      case Left(error) =>
        System.err.println(error)
        sys.exit(2)
      case Right(cliArgs) =>
        try {
          run(cliArgs)
        } catch {
          case e: Exception =>
            System.err.println(s"error: ${Option(e.getMessage).getOrElse(e.toString)}")
            sys.exit(1)
        }
    }

  /** The CLI's own baked-in defaults (genesis conf + CLI-local settings, see
    * `cli-defaults.conf`'s scaladoc-style header comment for why this is loaded by
    * explicit resource name rather than via `ConfigFactory.load()`'s usual
    * application.conf/reference.conf resolution), or - when `--conf <file>` is given -
    * that file entirely in its place (no fallback to the built-in defaults: pointing at a
    * different protocol instance means providing every key that instance needs, the same
    * way paideia-state's own `conf/application.conf` does for its genesis include).
    */
  private def loadConfig(confFileOpt: Option[String]): Config =
    confFileOpt match {
      case Some(path) =>
        val f = new File(path)
        if (!f.isFile)
          throw new IllegalArgumentException(s"--conf file not found: $path")
        ConfigFactory.parseFile(f).resolve()
      case None =>
        ConfigFactory.parseResources("cli-defaults.conf").resolve()
    }

  /** `~/.paideia/<network>` (lowercased `NetworkType`, e.g. `mainnet`/`testnet`), created
    * if missing, or `--data-dir`'s value verbatim when given. This is `PaideiaSession`'s
    * `storeRoot`: `state/`, `daoconfigs/`, `proposals/` and `stakingStates/` all live
    * under it.
    */
  private def dataDirFor(env: PaideiaEnv, overrideDir: Option[String]): File = {
    val dir = overrideDir
      .map(new File(_))
      .getOrElse(
        new File(
          new File(System.getProperty("user.home"), ".paideia"),
          env.networkType.toString.toLowerCase
        )
      )
    dir.mkdirs()
    dir
  }

  private def run(cliArgs: CliArgs): Unit = {
    val builtConf = loadConfig(cliArgs.confFile)
    // A --node override wins over both the baked-in default and $ERGO_NODE, so it's
    // layered on top rather than folded into loadConfig.
    val effectiveConf = cliArgs.node match {
      case Some(nodeUrl) =>
        ConfigFactory
          .parseString("paideia.node = \"" + nodeUrl + "\"")
          .withFallback(builtConf)
          .resolve()
      case None => builtConf
    }

    val env     = new PaideiaEnv(effectiveConf.getConfig("paideia"))
    val dataDir = dataDirFor(env, cliArgs.dataDir)
    val session = PaideiaSession(env, dataDir)
    Paideia.setDefault(session)

    try {
      val nodeUrl = env.conf.getString("node")
      // Per the design doc (D3): node-based, no explorer dependency. Built directly over
      // the node-only NodeDataSourceImpl rather than through RestApiErgoClient: in appkit
      // 6.0.1 even RestApiErgoClient.createWithoutExplorer constructs a
      // NodeAndExplorerDataSourceImpl (with a null explorer client, whose constructor
      // then throws "For node-only use, use NodeDataSourceImpl") - so the ErgoClient
      // wrapper is skipped entirely and the BlockchainContext it would have produced is
      // built the same way its BlockchainContextBuilderImpl path would.
      val datasource = new NodeDataSourceImpl(new ApiClient(nodeUrl))
      val ctx = new BlockchainContextBuilderImpl(datasource, env.networkType)
        .build()
        .asInstanceOf[BlockchainContextImpl]
      val blockSource = new NodeBlockSource(
        datasource,
        onRetry = (desc, attempt, message) =>
          System.err.println(s"[retry] $desc (attempt $attempt): $message")
      )
      val indexedClient   = IndexedNodeClient.forNode(nodeUrl)
      val userBoxSelector = UserBoxSelector(indexedClient, ctx)
      val lifecycle = new StateLifecycle(
        session,
        blockSource,
        indexedClient,
        checkpointInterval = env.conf.getInt("checkpointInterval"),
        log                = msg => System.err.println(msg)
      )

      val height = lifecycle.bringUpToDate(ctx)
      System.err.println(s"[sync] up to date at height $height")

      cliArgs.command match {
        case Command.Sync => ()
        case Command.DaoList =>
          printDaoList(ReadModels.daoList())
        case Command.ProposalList(daoKey) =>
          printProposalList(ReadModels.proposalList(ctx, daoKey))
        case Command.ProposalShow(daoKey, index) =>
          printProposalDetail(ReadModels.proposalDetail(ctx, daoKey, index))

        case Command.StakeStatus(daoKey) =>
          val addresses  = requireAddresses(cliArgs)
          val candidates = ReadModels.candidateStakeKeysFor(userBoxSelector, addresses)
          printStakeStatus(ReadModels.stakeStatus(ctx, daoKey, candidates))

        case Command.StakeAdd(daoKey, amount, stakeKeyOverride) =>
          val addresses = requireAddresses(cliArgs)
          val existingKey = stakeKeyOverride.orElse(
            findStake(ctx, userBoxSelector, daoKey, addresses).map(_.stakeKey)
          )
          val tx = existingKey match {
            case Some(key) =>
              UserTransactions.addStake(
                ctx,
                userBoxSelector,
                daoKey,
                key,
                amount,
                addresses,
                addresses.head
              )
            case None =>
              UserTransactions
                .stake(ctx, userBoxSelector, daoKey, amount, addresses, addresses.head)
          }
          val description = existingKey match {
            case Some(key) => s"add $amount to existing stake $key on DAO $daoKey"
            case None      => s"first-time stake of $amount on DAO $daoKey"
          }
          signOrPrint(tx, cliArgs, description)

        case Command.StakeRemove(daoKey, removeAmount) =>
          val addresses = requireAddresses(cliArgs)
          val stakeInfo = findStake(ctx, userBoxSelector, daoKey, addresses).getOrElse(
            throw new IllegalArgumentException(
              s"no stake found for DAO $daoKey at the given --address(es)"
            )
          )
          val remaining = removeAmount match {
            case RemoveAmount.All => 0L
            case RemoveAmount.Exact(v) =>
              val r = stakeInfo.stake.stake - v
              if (r < 0)
                throw new IllegalArgumentException(
                  s"cannot remove $v: only ${stakeInfo.stake.stake} currently staked"
                )
              r
          }
          // Removal always withdraws every pending reward too, regardless of how much
          // stake is being removed - see RemoveAmount's scaladoc.
          val newRecord = StakeRecord(
            remaining,
            stakeInfo.stake.lockedUntil,
            stakeInfo.stake.rewards.map(_ => 0L)
          )
          val tx = UserTransactions.unstake(
            ctx,
            userBoxSelector,
            daoKey,
            stakeInfo.stakeKey,
            newRecord,
            addresses,
            addresses.head
          )
          val amountLabel = removeAmount match {
            case RemoveAmount.All      => s"the entire stake (${stakeInfo.stake.stake})"
            case RemoveAmount.Exact(v) => v.toString
          }
          signOrPrint(
            tx,
            cliArgs,
            s"withdraw $amountLabel from stake ${stakeInfo.stakeKey} on DAO $daoKey " +
              "(this also withdraws all pending rewards)"
          )

        case Command.Vote(daoKey, proposalIndex, votes) =>
          val addresses = requireAddresses(cliArgs)
          val stakeInfo = findStake(ctx, userBoxSelector, daoKey, addresses).getOrElse(
            throw new IllegalArgumentException(
              s"no stake found for DAO $daoKey - you must stake before voting"
            )
          )
          val tx = UserTransactions.vote(
            ctx,
            userBoxSelector,
            daoKey,
            stakeInfo.stakeKey,
            proposalIndex,
            votes.toArray,
            addresses,
            addresses.head
          )
          signOrPrint(
            tx,
            cliArgs,
            s"vote [${votes.mkString(",")}] on proposal $proposalIndex of DAO $daoKey " +
              s"using stake ${stakeInfo.stakeKey}"
          )
      }
    } finally {
      session.close()
    }
  }

  private def formatInstant(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).toString

  /** Every W4a transaction command needs at least one `--address` - both as the source of
    * wallet funds/tokens `UserBoxSelector` draws `userInputs` from, and, for its first
    * entry, as the change/receive address (see `CliArgs.addresses`'s scaladoc).
    */
  private def requireAddresses(cliArgs: CliArgs): List[String] =
    if (cliArgs.addresses.isEmpty)
      throw new IllegalArgumentException(
        "this command requires at least one --address"
      )
    else cliArgs.addresses

  /** Auto-detects an existing stake for `daoKey` among `addresses`' own wallet contents -
    * the same lookup `stake status` prints, reused by `stake add` (to decide between a
    * `StakeTransaction` and an `AddStakeTransaction` - see
    * `im.paideia.app.UserTransactions`), `stake remove` and `vote` (which both require an
    * existing stake key). Picks the first match when more than one of the addresses'
    * tokens happens to resolve to a live stake record - in practice a wallet holds at
    * most one stake key per DAO.
    */
  private def findStake(
    ctx: BlockchainContextImpl,
    selector: UserBoxSelector,
    daoKey: String,
    addresses: List[String]
  ): Option[StakeInfo] = {
    val candidates = ReadModels.candidateStakeKeysFor(selector, addresses)
    ReadModels.stakeStatus(ctx, daoKey, candidates).headOption
  }

  private def printStakeStatus(stakes: List[StakeInfo]): Unit =
    if (stakes.isEmpty) println("no stake found")
    else
      stakes.foreach { s =>
        val lockedUntil =
          if (s.stake.lockedUntil <= 0) "not locked"
          else formatInstant(s.stake.lockedUntil)
        val participation = s.participation
          .map(p => s"voted=${p.voted} votedTotal=${p.votedTotal}")
          .getOrElse("no participation record yet")
        println(
          s"stake key ${s.stakeKey}: staked=${s.stake.stake} lockedUntil=$lockedUntil " +
            s"rewards=[${s.stake.rewards.mkString(",")}] ($participation)"
        )
      }

  /** The local signing server's default port (`--port` overrides it). */
  private val defaultPort = 8077

  /** How long to wait for a wallet to sign and submit before giving up (`sys.exit(1)`).
    */
  private val signingTimeoutMs = 15L * 60L * 1000L

  /** Finishes every W4a transaction command: builds the tx's unsigned form exactly once
    * (`PaideiaTransaction.unsigned()` mutates the tx's own `outputs` to add a change box
    * \- calling it twice would add two), then either dumps `--no-sign` JSON and returns,
    * or starts the local signing server and waits for a signature (see
    * [[signAndSubmit]]).
    */
  private def signOrPrint(
    tx: PaideiaTransaction,
    cliArgs: CliArgs,
    actionDescription: String
  ): Unit = {
    val unsignedTx = tx.unsigned()
    if (cliArgs.noSign) printNoSign(unsignedTx, tx)
    else
      signAndSubmit(
        unsignedTx,
        tx,
        cliArgs.port.getOrElse(defaultPort),
        actionDescription
      )
  }

  /** `--no-sign`: `{txId, reducedTx, eip12UnsignedTx}`, no server started. `reducedTx` is
    * still included (base64url of `tx.ctx.newProverBuilder.build.reduce(unsignedTx,
    * 0).toBytes()`, the same payload the signing server would hand an ErgoPay wallet) so
    * this output is independently usable by a caller with its own signing/submission
    * pipeline.
    */
  private def printNoSign(
    unsignedTx: UnsignedTransaction,
    tx: PaideiaTransaction
  ): Unit = {
    val reducedTx = tx.ctx.newProverBuilder().build().reduce(unsignedTx, 0)
    val payload   = Base64.getUrlEncoder.encodeToString(reducedTx.toBytes())

    val root = new JsonObject()
    root.addProperty("txId", unsignedTx.getId())
    root.addProperty("reducedTx", payload)
    root.add("eip12UnsignedTx", Eip12UnsignedTx.toJsonObject(Eip12UnsignedTx(unsignedTx)))
    println(root.toString)
  }

  /** The default signing flow: starts a [[SigningServer]] exposing `unsignedTx`, prints
    * the ErgoPay URI (+ QR code, for a mobile wallet) and the Nautilus-in-the-browser
    * page URL, then blocks in [[waitForSubmission]] until one of them completes (or the
    * 15 minute timeout hits, in which case this exits non-zero).
    */
  private def signAndSubmit(
    unsignedTx: UnsignedTransaction,
    tx: PaideiaTransaction,
    port: Int,
    actionDescription: String
  ): Unit = {
    val reducedTx    = tx.ctx.newProverBuilder().build().reduce(unsignedTx, 0)
    val payload      = Base64.getUrlEncoder.encodeToString(reducedTx.toBytes())
    val eip12Json    = Eip12UnsignedTx.toJson(Eip12UnsignedTx(unsignedTx))
    val expectedTxId = unsignedTx.getId()
    val nodeUrl      = Env.conf.getString("node")

    val server =
      SigningServer(port, payload, eip12Json, actionDescription, nodeUrl)
    server.start()
    try {
      val host        = lanIp()
      val ergopayUri  = s"ergopay://$host:$port/tx"
      val nautilusUrl = s"http://$host:$port/"

      println(actionDescription)
      println()
      println(s"scan with an Ergo mobile wallet (ErgoPay): $ergopayUri")
      print(Qr.render(ergopayUri))
      println()
      println(s"or sign in a browser with Nautilus: $nautilusUrl")
      println()
      println(
        s"waiting up to 15 minutes for a signature (expected transaction id: $expectedTxId)..."
      )

      waitForSubmission(server, nodeUrl, expectedTxId, signingTimeoutMs) match {
        case Some(txId) => println(s"submitted: $txId")
        case None =>
          System.err.println("timed out waiting for a signature")
          sys.exit(1)
      }
    } finally server.stop()
  }

  /** Polls, every 5 seconds, for either the local `/signed` handler having accepted a
    * wallet-submitted tx (the Nautilus path) or `expectedTxId` showing up at the node
    * itself, unconfirmed or confirmed (the ErgoPay path - a mobile wallet submits
    * directly to the network, never touching this server - see [[SigningServer]]'s
    * scaladoc). Ergo transaction ids are computed from inputs/dataInputs/outputs only,
    * never from signatures, so `expectedTxId` (computed from the *unsigned* tx) is
    * exactly the id the signed, broadcast transaction will have.
    */
  private def waitForSubmission(
    server: SigningServer,
    nodeUrl: String,
    expectedTxId: String,
    timeoutMs: Long
  ): Option[String] = {
    val deadline               = System.currentTimeMillis() + timeoutMs
    var result: Option[String] = None
    while (result.isEmpty && System.currentTimeMillis() < deadline) {
      server.submittedTxId match {
        case some @ Some(_) => result = some
        case None =>
          server
            .takeError()
            .foreach(err =>
              System.err.println(s"[signing] a signed submission failed: $err")
            )
          if (
            Try(NodeHttp.isUnconfirmed(nodeUrl, expectedTxId)).getOrElse(false) ||
            Try(NodeHttp.isConfirmed(nodeUrl, expectedTxId)).getOrElse(false)
          ) {
            result = Some(expectedTxId)
          } else {
            Thread.sleep(5000)
          }
      }
    }
    result
  }

  /** The first non-loopback site-local IPv4 address of any up network interface, or
    * `127.0.0.1` if none is found (e.g. offline) - used to build a QR-scannable
    * `ergopay://`/`http://` URL another device on the same LAN (a phone running an Ergo
    * wallet) can actually reach; `localhost` itself would only work for a wallet running
    * on this same machine.
    */
  private def lanIp(): String = {
    val candidate = NetworkInterface.getNetworkInterfaces.asScala
      .filter(ni => Try(ni.isUp && !ni.isLoopback).getOrElse(false))
      .flatMap(ni => ni.getInetAddresses.asScala)
      .collectFirst {
        case a: java.net.Inet4Address if a.isSiteLocalAddress => a.getHostAddress
      }
    candidate.getOrElse("127.0.0.1")
  }

  /** Abbreviates a hex id (box id, token id) to its first 8 hex characters + "..", for
    * compact terminal output; the full id is always available separately (box ids in
    * `proposal list`, token ids aren't otherwise surfaced by this v1 read-only surface).
    */
  private def shortId(id: String): String =
    if (id.length > 10) id.take(8) + ".." else id

  private def printDaoList(daos: List[DaoSummary]): Unit =
    if (daos.isEmpty) println("no DAOs found")
    else
      daos
        .sortBy(_.name)
        .foreach(d =>
          println(
            s"${d.key}  ${d.name}  (config box height ${d.configBoxCreationHeight})"
          )
        )

  /** Renders `ProposalSummary.passed` (see there): -1 running, -2 evaluated but
    * threshold/quorum not met, otherwise the winning option's index.
    */
  private def statusOf(passed: Int): String = passed match {
    case -1 => "running"
    case -2 => "failed (threshold/quorum not met)"
    case n  => s"passed (option $n)"
  }

  private def printProposalList(proposals: List[ProposalSummary]): Unit =
    if (proposals.isEmpty) println("no proposals found")
    else
      proposals.foreach { p =>
        println(
          s"#${p.index} ${p.name} - end=${formatInstant(p.endTime)} " +
            s"votes=${p.totalVotes} tallies=[${p.voteCounts.mkString(",")}] " +
            s"[${statusOf(p.passed)}] box=${p.boxId}"
        )
      }

  private def renderAction(action: ActionView): List[String] = action match {
    case sf: SendFundsActionView =>
      val outputs = sf.outputs.map { o =>
        val erg    = o.nanoErg.toDouble / 1000000000.0
        val tokens = o.tokens.map { case (id, amount) => s"$amount ${shortId(id)}" }
        val extra  = if (tokens.isEmpty) "" else " + " + tokens.mkString(", ")
        f"$erg%.3f ERG$extra to ${o.address}"
      }
      List(
        s"option ${sf.optionId} (activates ${formatInstant(sf.activationTime)}) -> " +
          s"send ${outputs.mkString("; ")}"
      )
    case uc: UpdateConfigActionView =>
      val header =
        s"option ${uc.optionId} (activates ${formatInstant(uc.activationTime)}) -> update config"
      val lines = uc.remove.map(k => s"  remove $k") ++
        uc.update.map(e => s"  ${e.key}: ${e.valueType} -> ${e.value}") ++
        uc.insert.map(e => s"  insert ${e.key}: ${e.valueType} = ${e.value}")
      header :: lines
  }

  private def printProposalDetail(detail: ProposalDetail): Unit = {
    val s = detail.summary
    println(s"#${s.index} ${s.name}")
    println(s"end: ${formatInstant(s.endTime)}")
    println(s"total votes: ${s.totalVotes}  tallies: [${s.voteCounts.mkString(",")}]")
    println(s"status: ${statusOf(s.passed)}")
    println(s"box: ${s.boxId}")
    println()
    if (detail.actions.isEmpty) println("no actions")
    else detail.actions.flatMap(renderAction).foreach(println)
    println()
    println(s"${detail.votes.size} voter(s):")
    detail.votes.foreach { case (voter, weights) =>
      println(s"  $voter: [${weights.mkString(",")}]")
    }
  }
}
