package im.paideia.cli

/** How much of an existing stake `stake remove` withdraws - see [[Command.StakeRemove]].
  */
sealed trait RemoveAmount
object RemoveAmount {

  /** Remove exactly `amount` (raw base units) of the current stake. */
  final case class Exact(amount: Long) extends RemoveAmount

  /** Remove the entire current stake (`stake remove <daoKey> all`). */
  case object All extends RemoveAmount
}

/** One of the CLI's subcommands, as parsed by [[ArgParser]]. Every command implies a
  * `sync` first (see `Main.run`) - `Sync` itself just stops there instead of running a
  * query afterwards.
  *
  * `StakeStatus`/`StakeAdd`/`StakeRemove`/`Vote` are W4a's user-transaction commands:
  * they all require at least one `--address` (see [[CliArgs.addresses]]) and, unlike the
  * W3 read-only commands, build a real direct protocol transaction (the same
  * `StakeTransaction`/`AddStakeTransaction`/`UnstakeTransaction`/`CastVoteTransaction`
  * the SDK already has - see `im.paideia.app.UserTransactions`) and end by asking the
  * user to sign it - by default via a short-lived local signing server (ErgoPay QR code +
  * a Nautilus-in-the-browser page - see `Main.signAndSubmit`/`SigningServer`), or a
  * `--no-sign` JSON dump instead.
  */
sealed trait Command

object Command {
  case object Sync extends Command
  case object DaoList extends Command
  final case class ProposalList(daoKey: String) extends Command
  final case class ProposalShow(daoKey: String, index: Int) extends Command

  /** `stake status <daoKey>` - lists every stake this session's `--address`es hold for
    * `daoKey`.
    */
  final case class StakeStatus(daoKey: String) extends Command

  /** `stake add <daoKey> <amount>` (optionally `--stake-key <id>`) - `amount` is raw
    * token base units (the DAO's governance token has whatever decimals it has; the CLI
    * never rescales it). When `stakeKeyOverride` is `None`, `Main` auto-detects an
    * existing stake key for `daoKey` among the `--address`es' own tokens (via
    * `ReadModels.stakeStatus`) and builds an `AddStakeTransaction` if one is found, or a
    * first-time `StakeTransaction` otherwise (see `im.paideia.app.UserTransactions`).
    */
  final case class StakeAdd(
    daoKey: String,
    amount: Long,
    stakeKeyOverride: Option[String]
  ) extends Command

  /** `stake remove <daoKey> <amount|all>` - `amount` (raw base units) or the literal
    * `all`. Removal always withdraws every pending reward too, regardless of `amount` -
    * see [[RemoveAmount]] and `Main`'s handling of this command.
    */
  final case class StakeRemove(daoKey: String, amount: RemoveAmount) extends Command

  /** `vote <daoKey> <proposalIndex> <v1,v2,...>` - `votes` is the per-option vote
    * allocation, comma-separated, in option order.
    */
  final case class Vote(daoKey: String, proposalIndex: Int, votes: List[Long])
    extends Command
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
  * @param addresses
  *   every `--address <addr>` given, in the order given; the first is also this
  *   transaction's change/receive address. Required (non-empty) for every W4a transaction
  *   command; unused by the W3 read-only commands.
  * @param noSign
  *   `--no-sign` - print `{txId, reducedTx, eip12UnsignedTx}` as JSON and exit instead of
  *   starting the local signing server (see `Main.signAndSubmit`/`Main.printNoSign`).
  *   Ignored by commands that don't build a transaction.
  * @param port
  *   `--port <n>` - the local signing server's port (default `8077`, see
  *   `Main.defaultPort`). Ignored when `noSign` is set (no server is started).
  */
final case class CliArgs(
  node: Option[String],
  dataDir: Option[String],
  confFile: Option[String],
  command: Command,
  addresses: List[String] = Nil,
  noSign: Boolean         = false,
  port: Option[Int]       = None
)

/** Hand-rolled parsing for the CLI's `paideia [--node <url>] [--data-dir <dir>] [--conf
  * <file>] [--address <addr>]... [--no-sign] <command>` surface (see `Main`'s scaladoc
  * for the full usage text) - no argument-parsing library dependency, per the design
  * doc's "no new library dependencies" constraint. Pure and side-effect-free so it's
  * directly unit-testable without touching `Main`'s node/session wiring - see
  * `ArgParserSuite`.
  */
object ArgParser {

  val usage: String =
    """paideia [--node <url>] [--data-dir <dir>] [--conf <file>] <command>
      |  sync                          bring local state current and verify, then exit
      |  dao list                      DAOs known to this protocol instance
      |  proposal list <daoKey>        running + recent proposals, tallies, deadlines
      |  proposal show <daoKey> <index> full detail incl. decoded on-chain actions
      |
      |User transaction commands (require at least one --address; see below):
      |  stake status <daoKey>                  stake key(s), staked amount, lockedUntil, rewards
      |  stake add <daoKey> <amount> [--stake-key <id>]
      |                                          stake (or add to an existing stake) <amount>
      |                                          raw base units of the DAO's governance token
      |  stake remove <daoKey> <amount|all>     withdraw <amount> (or the whole stake with
      |                                          'all') - also withdraws all pending rewards
      |  vote <daoKey> <proposalIndex> <v1,v2,...>
      |                                          cast a vote, per-option allocation
      |
      |Common flags for every command:
      |  --node <url>          Ergo node to use (overrides $ERGO_NODE and the built-in default)
      |  --data-dir <dir>      local state directory (default: ~/.paideia/<network>)
      |  --conf <file>         replace the built-in genesis + CLI defaults entirely
      |  --address <addr>      repeatable; required by every transaction command. The
      |                        first --address given is also the change/receive address.
      |  --no-sign             print {txId, reducedTx, eip12UnsignedTx} as JSON and exit,
      |                        instead of starting the local signing server
      |  --port <n>            local signing server port (default 8077)
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
      confFile: Option[String],
      addresses: List[String],
      noSign: Boolean,
      port: Option[Int]
    ): Either[String, CliArgs] =
      remaining match {
        case "--node" :: value :: rest =>
          loop(rest, Some(value), dataDir, confFile, addresses, noSign, port)
        case "--node" :: Nil => Left("--node requires a value")
        case "--data-dir" :: value :: rest =>
          loop(rest, node, Some(value), confFile, addresses, noSign, port)
        case "--data-dir" :: Nil => Left("--data-dir requires a value")
        case "--conf" :: value :: rest =>
          loop(rest, node, dataDir, Some(value), addresses, noSign, port)
        case "--conf" :: Nil => Left("--conf requires a value")
        case "--address" :: value :: rest =>
          loop(rest, node, dataDir, confFile, addresses :+ value, noSign, port)
        case "--address" :: Nil => Left("--address requires a value")
        case "--no-sign" :: rest =>
          loop(rest, node, dataDir, confFile, addresses, true, port)
        case "--port" :: value :: rest =>
          scala.util.Try(value.toInt).toOption match {
            case Some(p) =>
              loop(rest, node, dataDir, confFile, addresses, noSign, Some(p))
            case None => Left(s"invalid --port: '$value' is not an integer")
          }
        case "--port" :: Nil => Left("--port requires a value")
        case rest =>
          parseCommand(rest).map(cmd =>
            CliArgs(node, dataDir, confFile, cmd, addresses, noSign, port)
          )
      }
    loop(args, None, None, None, Nil, false, None)
  }

  private def parseLong(desc: String, s: String): Either[String, Long] =
    scala.util.Try(s.toLong).toOption match {
      case Some(v) => Right(v)
      case None    => Left(s"invalid $desc: '$s' is not an integer")
    }

  private def parseVotes(s: String): Either[String, List[Long]] = {
    val parts  = s.split(",", -1).toList
    val parsed = parts.map(p => scala.util.Try(p.trim.toLong).toOption)
    if (parts.nonEmpty && parsed.forall(_.isDefined)) Right(parsed.flatten)
    else Left(s"invalid vote allocation: '$s' - expected comma-separated integers")
  }

  private def parseRemoveAmount(s: String): Either[String, RemoveAmount] =
    if (s == "all") Right(RemoveAmount.All)
    else
      parseLong("remove amount", s).flatMap {
        case v if v > 0 => Right(RemoveAmount.Exact(v))
        case v          => Left(s"invalid remove amount: '$v' must be positive or 'all'")
      }

  private def parseStakeAdd(tokens: List[String]): Either[String, Command] =
    tokens match {
      case daoKey :: amountStr :: Nil =>
        parseLong("stake amount", amountStr).map(amount =>
          Command.StakeAdd(daoKey, amount, None)
        )
      case daoKey :: amountStr :: "--stake-key" :: stakeKey :: Nil =>
        parseLong("stake amount", amountStr).map(amount =>
          Command.StakeAdd(daoKey, amount, Some(stakeKey))
        )
      case _ =>
        Left(s"invalid 'stake add' arguments: ${tokens.mkString(" ")}\n\n" + usage)
    }

  private def parseCommand(tokens: List[String]): Either[String, Command] = tokens match {
    case "sync" :: Nil                         => Right(Command.Sync)
    case "dao" :: "list" :: Nil                => Right(Command.DaoList)
    case "proposal" :: "list" :: daoKey :: Nil => Right(Command.ProposalList(daoKey))
    case "proposal" :: "show" :: daoKey :: indexStr :: Nil =>
      parseLong("proposal index", indexStr).map(index =>
        Command.ProposalShow(daoKey, index.toInt)
      )
    case "stake" :: "status" :: daoKey :: Nil => Right(Command.StakeStatus(daoKey))
    case "stake" :: "add" :: rest             => parseStakeAdd(rest)
    case "stake" :: "remove" :: daoKey :: amountStr :: Nil =>
      parseRemoveAmount(amountStr).map(amount => Command.StakeRemove(daoKey, amount))
    case "vote" :: daoKey :: indexStr :: votesStr :: Nil =>
      for {
        index <- parseLong("proposal index", indexStr)
        votes <- parseVotes(votesStr)
      } yield Command.Vote(daoKey, index.toInt, votes)
    case Nil =>
      Left("missing command\n\n" + usage)
    case other =>
      Left(s"unrecognized command: ${other.mkString(" ")}\n\n" + usage)
  }
}
