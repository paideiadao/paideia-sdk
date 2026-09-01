package im.paideia.cli

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
    * `daoKey` (or, with `--stake-key`, that specific key's record).
    */
  final case class StakeStatus(daoKey: String) extends Command

  /** `stake add <daoKey> <amount>` - `amount` is raw token base units (the DAO's
    * governance token has whatever decimals it has; the CLI never rescales it), and must
    * be positive. `Main` auto-detects an existing stake key for `daoKey` among the
    * `--address`es' own tokens (via `ReadModels.stakeStatus`) unless `--stake-key`
    * overrides that - see [[CliArgs.stakeKeyOverride]] - and builds an
    * `AddStakeTransaction` if one is found, or a first-time `StakeTransaction` otherwise
    * (see `im.paideia.app.UserTransactions`).
    */
  final case class StakeAdd(daoKey: String, amount: Long) extends Command

  /** `stake remove <daoKey>` - a FULL unstake (all stake and all pending rewards
    * withdrawn, the stake key NFT burned). There is no partial-unstake variant: the
    * `ChangeStake` companion contract's `noPartialUnstake` conjunct
    * (`ChangeStake.es:114`, `newStakeAmount >= currentStakeAmount`) rejects any on-chain
    * reduction of an existing stake, so an amount argument here would only ever describe
    * a transaction the protocol refuses to confirm - see `ArgParser.parseCommand`'s
    * handling of a stray amount argument for the error this produces instead.
    */
  final case class StakeRemove(daoKey: String) extends Command

  /** `vote <daoKey> <proposalIndex> <v1,v2,...>` - `votes` is the per-option vote
    * allocation, comma-separated, in option order; every element must be non-negative
    * (checked at parse time - see [[ArgParser.parseVotes]]). `Main` additionally rejects
    * a total allocation exceeding the voter's current staked amount before building the
    * transaction.
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
  *   `--port <n>` - the local signing server's port, `1`-`65535` (default `8077`, see
  *   `Main.defaultPort`). Ignored when `noSign` is set (no server is started).
  * @param stakeKeyOverride
  *   `--stake-key <id>` - picks a specific stake key rather than auto-detecting one from
  *   `addresses`' own wallet contents. A single global flag (not parsed per-command)
  *   since `stake add`, `stake remove` and `vote` all accept it; required whenever
  *   auto-detection would otherwise be ambiguous (more than one stake key found - see
  *   `Main.findStake`).
  * @param host
  *   `--host <ip-or-name>` - overrides the host `Main` advertises in the printed
  *   `ergopay://`/`http://` URLs (the local signing server still binds every interface
  *   regardless - this only changes what's shown/encoded for a wallet to connect back
  *   to). Defaults to `Main.lanIp()`'s own auto-detection.
  */
final case class CliArgs(
  node: Option[String],
  dataDir: Option[String],
  confFile: Option[String],
  command: Command,
  addresses: List[String]          = Nil,
  noSign: Boolean                  = false,
  port: Option[Int]                = None,
  stakeKeyOverride: Option[String] = None,
  host: Option[String]             = None
)

/** Hand-rolled parsing for the CLI's `paideia [--node <url>] [--data-dir <dir>] [--conf
  * <file>] [--address <addr>]... [--no-sign] [--port <n>] [--stake-key <id>] [--host
  * <ip-or-name>] <command>` surface (see `Main`'s scaladoc for the full usage text) - no
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
      |
      |User transaction commands (require at least one --address; see below):
      |  stake status <daoKey>          stake key(s), staked amount, lockedUntil, rewards
      |  stake add <daoKey> <amount>    stake (or add to an existing stake) <amount> raw
      |                                 base units of the DAO's governance token
      |  stake remove <daoKey>          full unstake - withdraws everything (all stake +
      |                                 all pending rewards); the protocol has no partial
      |                                 unstake, so this takes no amount argument
      |  vote <daoKey> <proposalIndex> <v1,v2,...>
      |                                 cast a vote, per-option allocation
      |
      |Common flags for every command (must precede the command):
      |  --node <url>          Ergo node to use (overrides $ERGO_NODE and the built-in default)
      |  --data-dir <dir>      local state directory (default: ~/.paideia/<network>)
      |  --conf <file>         replace the built-in genesis + CLI defaults entirely
      |  --address <addr>      repeatable; required by every transaction command. The
      |                        first --address given is also the change/receive address.
      |  --stake-key <id>      pick a specific stake key for stake add/remove/vote,
      |                        instead of auto-detecting one from --address's own wallet
      |  --no-sign             print {txId, reducedTx, eip12UnsignedTx} as JSON and exit,
      |                        instead of starting the local signing server
      |  --port <n>            local signing server port, 1-65535 (default 8077)
      |  --host <ip-or-name>   host to advertise in the printed signing URLs (default:
      |                        auto-detected LAN IP)
      |""".stripMargin

  /** Parses `args` into a [[CliArgs]], or a human-readable error (never including
    * `args.toString` verbatim, so it's a fit stderr message on its own) describing what
    * went wrong.
    *
    * Flags must precede the command (`--node ... --conf ... <command> ...`), matching
    * [[usage]] - a `--flag`-shaped token found anywhere in the command's own arguments
    * (not just interleaved immediately after the command name) is rejected with a message
    * saying so, rather than being confused for a positional argument or an unrecognized
    * command.
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
      port: Option[Int],
      stakeKey: Option[String],
      host: Option[String]
    ): Either[String, CliArgs] =
      remaining match {
        case "--node" :: value :: rest =>
          loop(
            rest,
            Some(value),
            dataDir,
            confFile,
            addresses,
            noSign,
            port,
            stakeKey,
            host
          )
        case "--node" :: Nil => Left("--node requires a value")
        case "--data-dir" :: value :: rest =>
          loop(rest, node, Some(value), confFile, addresses, noSign, port, stakeKey, host)
        case "--data-dir" :: Nil => Left("--data-dir requires a value")
        case "--conf" :: value :: rest =>
          loop(rest, node, dataDir, Some(value), addresses, noSign, port, stakeKey, host)
        case "--conf" :: Nil => Left("--conf requires a value")
        case "--address" :: value :: rest =>
          loop(
            rest,
            node,
            dataDir,
            confFile,
            addresses :+ value,
            noSign,
            port,
            stakeKey,
            host
          )
        case "--address" :: Nil => Left("--address requires a value")
        case "--stake-key" :: value :: rest =>
          loop(rest, node, dataDir, confFile, addresses, noSign, port, Some(value), host)
        case "--stake-key" :: Nil => Left("--stake-key requires a value")
        case "--host" :: value :: rest =>
          loop(
            rest,
            node,
            dataDir,
            confFile,
            addresses,
            noSign,
            port,
            stakeKey,
            Some(value)
          )
        case "--host" :: Nil => Left("--host requires a value")
        case "--no-sign" :: rest =>
          loop(rest, node, dataDir, confFile, addresses, true, port, stakeKey, host)
        case "--port" :: value :: rest =>
          scala.util.Try(value.toInt).toOption match {
            case Some(p) if p >= 1 && p <= 65535 =>
              loop(
                rest,
                node,
                dataDir,
                confFile,
                addresses,
                noSign,
                Some(p),
                stakeKey,
                host
              )
            case Some(p) =>
              Left(s"invalid --port: $p is out of range (must be 1-65535)")
            case None => Left(s"invalid --port: '$value' is not an integer")
          }
        case "--port" :: Nil => Left("--port requires a value")
        case rest =>
          parseCommand(rest).map(cmd =>
            CliArgs(node, dataDir, confFile, cmd, addresses, noSign, port, stakeKey, host)
          )
      }
    loop(args, None, None, None, Nil, false, None, None, None)
  }

  private def parseLong(desc: String, s: String): Either[String, Long] =
    scala.util.Try(s.toLong).toOption match {
      case Some(v) => Right(v)
      case None    => Left(s"invalid $desc: '$s' is not an integer")
    }

  /** Parses a comma-separated per-option vote allocation. Every element must parse as an
    * integer and be non-negative (a negative "vote" has no protocol meaning - M3) -
    * either failure produces a single, specific error rather than a generic parse
    * failure.
    */
  private[cli] def parseVotes(s: String): Either[String, List[Long]] = {
    val parts  = s.split(",", -1).toList
    val parsed = parts.map(p => scala.util.Try(p.trim.toLong).toOption)
    if (parts.isEmpty || parsed.exists(_.isEmpty))
      Left(s"invalid vote allocation: '$s' - expected comma-separated integers")
    else {
      val values = parsed.flatten
      if (values.exists(_ < 0))
        Left(
          s"invalid vote allocation: '$s' - individual allocations must not be negative"
        )
      else Right(values)
    }
  }

  private def parseStakeAdd(tokens: List[String]): Either[String, Command] =
    tokens match {
      case daoKey :: amountStr :: Nil =>
        parseLong("stake amount", amountStr).flatMap {
          case amount if amount > 0 => Right(Command.StakeAdd(daoKey, amount))
          case amount => Left(s"invalid stake amount: '$amount' must be positive")
        }
      case _ =>
        Left(s"invalid 'stake add' arguments: ${tokens.mkString(" ")}\n\n" + usage)
    }

  private def parseCommand(tokens: List[String]): Either[String, Command] =
    tokens.find(_.startsWith("--")) match {
      case Some(flag) =>
        Left(
          s"flags must come before the command: '$flag' was found after the command " +
            s"started\n\n" + usage
        )
      case None => parseCommandShape(tokens)
    }

  private def parseCommandShape(tokens: List[String]): Either[String, Command] =
    tokens match {
      case "sync" :: Nil                         => Right(Command.Sync)
      case "dao" :: "list" :: Nil                => Right(Command.DaoList)
      case "proposal" :: "list" :: daoKey :: Nil => Right(Command.ProposalList(daoKey))
      case "proposal" :: "show" :: daoKey :: indexStr :: Nil =>
        parseLong("proposal index", indexStr).map(index =>
          Command.ProposalShow(daoKey, index.toInt)
        )
      case "stake" :: "status" :: daoKey :: Nil => Right(Command.StakeStatus(daoKey))
      case "stake" :: "add" :: rest             => parseStakeAdd(rest)
      case "stake" :: "remove" :: daoKey :: Nil => Right(Command.StakeRemove(daoKey))
      case "stake" :: "remove" :: daoKey :: _ =>
        Left(
          "the protocol only supports full unstake: 'stake remove <daoKey>' takes no " +
            "amount argument (use --stake-key <id> if more than one stake key applies)" +
            "\n\n" + usage
        )
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
