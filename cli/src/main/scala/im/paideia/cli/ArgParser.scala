package im.paideia.cli

/** One of the CLI's subcommands, as parsed by [[ArgParser]]. Every command implies a
  * `sync` first (see `Main.run`) - `Sync` itself just stops there instead of running a
  * query afterwards.
  */
sealed trait Command

object Command {
  case object Sync extends Command
  case object DaoList extends Command
  final case class ProposalList(daoKey: String) extends Command
  final case class ProposalShow(daoKey: String, index: Int) extends Command
}

/** The global flags every command accepts, plus the parsed [[Command]] itself.
  *
  * @param node
  *   `--node <url>` override for `paideia.node` (also overridable via `$ERGO_NODE`, per
  *   the baked-in conf - this flag wins over both).
  * @param dataDir
  *   `--data-dir <dir>` override for where local state is stored; defaults to
  *   `~/.paideia/<network>` (see `Main.dataDirFor`).
  * @param confFile
  *   `--conf <file>` - when present, replaces the baked-in genesis + CLI defaults
  *   entirely, so a different protocol instance can be targeted without rebuilding the
  *   JAR.
  * @param command
  *   the parsed subcommand.
  */
final case class CliArgs(
  node: Option[String],
  dataDir: Option[String],
  confFile: Option[String],
  command: Command
)

/** Hand-rolled parsing for the CLI's `paideia [--node <url>] [--data-dir <dir>] [--conf
  * <file>] <command>` surface (see `Main`'s scaladoc for the full usage text) - no
  * argument-parsing library dependency, per the design doc's "no new library
  * dependencies" constraint. Pure and side-effect-free so it's directly unit-testable
  * without touching `Main`'s node/session wiring - see `ArgParserSuite`.
  */
object ArgParser {

  val usage: String =
    """paideia [--node <url>] [--data-dir <dir>] [--conf <file>] <command>
      |  sync                          bring local state current and verify, then exit
      |  dao list                      DAOs known to this protocol instance
      |  proposal list <daoKey>        running + recent proposals, tallies, deadlines
      |  proposal show <daoKey> <index> full detail incl. decoded on-chain actions
      |""".stripMargin

  /** Parses `args` into a [[CliArgs]], or a human-readable error (never including
    * `args.toString` verbatim, so it's a fit stderr message on its own) describing what
    * went wrong.
    *
    * Flags must precede the command (`--node ... --conf ... <command> ...`), matching
    * [[usage]] - there's no support for flags interleaved after the command starts.
    */
  def parse(args: Array[String]): Either[String, CliArgs] = parseTokens(args.toList)

  private[cli] def parseTokens(args: List[String]): Either[String, CliArgs] = {
    def loop(
      remaining: List[String],
      node: Option[String],
      dataDir: Option[String],
      confFile: Option[String]
    ): Either[String, CliArgs] =
      remaining match {
        case "--node" :: value :: rest     => loop(rest, Some(value), dataDir, confFile)
        case "--node" :: Nil               => Left("--node requires a value")
        case "--data-dir" :: value :: rest => loop(rest, node, Some(value), confFile)
        case "--data-dir" :: Nil           => Left("--data-dir requires a value")
        case "--conf" :: value :: rest     => loop(rest, node, dataDir, Some(value))
        case "--conf" :: Nil               => Left("--conf requires a value")
        case rest =>
          parseCommand(rest).map(cmd => CliArgs(node, dataDir, confFile, cmd))
      }
    loop(args, None, None, None)
  }

  private def parseCommand(tokens: List[String]): Either[String, Command] = tokens match {
    case "sync" :: Nil                         => Right(Command.Sync)
    case "dao" :: "list" :: Nil                => Right(Command.DaoList)
    case "proposal" :: "list" :: daoKey :: Nil => Right(Command.ProposalList(daoKey))
    case "proposal" :: "show" :: daoKey :: indexStr :: Nil =>
      scala.util.Try(indexStr.toInt).toOption match {
        case Some(index) => Right(Command.ProposalShow(daoKey, index))
        case None        => Left(s"invalid proposal index: '$indexStr' is not an integer")
      }
    case Nil =>
      Left("missing command\n\n" + usage)
    case other =>
      Left(s"unrecognized command: ${other.mkString(" ")}\n\n" + usage)
  }
}
