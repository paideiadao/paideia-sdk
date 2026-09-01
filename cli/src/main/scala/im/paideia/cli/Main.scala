package im.paideia.cli

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import im.paideia.Paideia
import im.paideia.PaideiaSession
import im.paideia.app.ActionView
import im.paideia.app.DaoSummary
import im.paideia.app.ProposalDetail
import im.paideia.app.ProposalSummary
import im.paideia.app.ReadModels
import im.paideia.app.SendFundsActionView
import im.paideia.app.StateLifecycle
import im.paideia.app.UpdateConfigActionView
import im.paideia.common.sync.IndexedNodeClient
import im.paideia.common.sync.NodeBlockSource
import im.paideia.util.PaideiaEnv
import org.ergoplatform.appkit.impl.BlockchainContextBuilderImpl
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.NodeDataSourceImpl
import org.ergoplatform.restapi.client.ApiClient

import java.io.File
import java.time.Instant

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
      val indexedClient = IndexedNodeClient.forNode(nodeUrl)
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
      }
    } finally {
      session.close()
    }
  }

  private def formatInstant(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).toString

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

  private def statusOf(passed: Option[Boolean]): String = passed match {
    case None        => "running"
    case Some(true)  => "passed"
    case Some(false) => "failed"
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
