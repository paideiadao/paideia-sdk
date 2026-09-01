package im.paideia.cli

import org.scalatest.funsuite.AnyFunSuite

/** Covers [[ArgParser.parse]] directly - no node, no session, pure argument parsing - per
  * the module's "the arg parser should live in a small testable object, test it too"
  * requirement.
  */
class ArgParserSuite extends AnyFunSuite {

  test("sync with no flags") {
    val result = ArgParser.parse(Array("sync"))
    assert(result == Right(CliArgs(None, None, None, Command.Sync)))
  }

  test("dao list") {
    val result = ArgParser.parse(Array("dao", "list"))
    assert(result == Right(CliArgs(None, None, None, Command.DaoList)))
  }

  test("proposal list <daoKey>") {
    val result = ArgParser.parse(Array("proposal", "list", "abc123"))
    assert(result == Right(CliArgs(None, None, None, Command.ProposalList("abc123"))))
  }

  test("proposal show <daoKey> <index>") {
    val result = ArgParser.parse(Array("proposal", "show", "abc123", "7"))
    assert(result == Right(CliArgs(None, None, None, Command.ProposalShow("abc123", 7))))
  }

  test("proposal show with a non-integer index is an error") {
    val result = ArgParser.parse(Array("proposal", "show", "abc123", "not-a-number"))
    assert(result.isLeft)
    assert(result.left.get.contains("not-a-number"))
  }

  test("flags before the command are all captured") {
    val result = ArgParser.parse(
      Array(
        "--node",
        "http://node:9053",
        "--data-dir",
        "/tmp/paideia-data",
        "--conf",
        "/tmp/custom.conf",
        "dao",
        "list"
      )
    )
    assert(
      result == Right(
        CliArgs(
          Some("http://node:9053"),
          Some("/tmp/paideia-data"),
          Some("/tmp/custom.conf"),
          Command.DaoList
        )
      )
    )
  }

  test("a single flag can be given alone") {
    val result = ArgParser.parse(Array("--node", "http://node:9053", "sync"))
    assert(result == Right(CliArgs(Some("http://node:9053"), None, None, Command.Sync)))
  }

  test("--node without a value is an error") {
    val result = ArgParser.parse(Array("--node"))
    assert(result == Left("--node requires a value"))
  }

  test("--data-dir without a value is an error") {
    val result = ArgParser.parse(Array("--data-dir"))
    assert(result == Left("--data-dir requires a value"))
  }

  test("--conf without a value is an error") {
    val result = ArgParser.parse(Array("--conf"))
    assert(result == Left("--conf requires a value"))
  }

  test("no command at all is an error mentioning usage") {
    val result = ArgParser.parse(Array())
    assert(result.isLeft)
    assert(result.left.get.contains("missing command"))
    assert(result.left.get.contains(ArgParser.usage))
  }

  test("an unrecognized command is an error mentioning usage") {
    val result = ArgParser.parse(Array("frobnicate"))
    assert(result.isLeft)
    assert(result.left.get.contains("unrecognized command"))
    assert(result.left.get.contains("frobnicate"))
  }

  test("proposal list without a daoKey is an error") {
    val result = ArgParser.parse(Array("proposal", "list"))
    assert(result.isLeft)
  }

  test("proposal show with too many arguments is an error") {
    val result = ArgParser.parse(Array("proposal", "show", "abc123", "7", "extra"))
    assert(result.isLeft)
  }

  test("stake status <daoKey>") {
    val result = ArgParser.parse(Array("stake", "status", "abc123"))
    assert(result == Right(CliArgs(None, None, None, Command.StakeStatus("abc123"))))
  }

  test("stake add <daoKey> <amount>") {
    val result = ArgParser.parse(Array("stake", "add", "abc123", "1000"))
    assert(
      result == Right(CliArgs(None, None, None, Command.StakeAdd("abc123", 1000L)))
    )
  }

  test("stake add with a non-integer amount is an error") {
    val result = ArgParser.parse(Array("stake", "add", "abc123", "not-a-number"))
    assert(result.isLeft)
    assert(result.left.get.contains("not-a-number"))
  }

  // m9
  test("stake add with a zero or negative amount is an error") {
    val zero = ArgParser.parse(Array("stake", "add", "abc123", "0"))
    assert(zero.isLeft)
    val negative = ArgParser.parse(Array("stake", "add", "abc123", "-5"))
    assert(negative.isLeft)
  }

  // C2
  test("stake remove <daoKey> - a full unstake, no amount argument") {
    val result = ArgParser.parse(Array("stake", "remove", "abc123"))
    assert(result == Right(CliArgs(None, None, None, Command.StakeRemove("abc123"))))
  }

  test("C2: stake remove with a numeric amount is a clear parse error, not a command") {
    val result = ArgParser.parse(Array("stake", "remove", "abc123", "500"))
    assert(result.isLeft)
    assert(result.left.get.contains("full unstake"))
  }

  test(
    "C2: stake remove <daoKey> all is also rejected (no such thing as 'remove all' anymore)"
  ) {
    val result = ArgParser.parse(Array("stake", "remove", "abc123", "all"))
    assert(result.isLeft)
    assert(result.left.get.contains("full unstake"))
  }

  test("stake remove without a daoKey is an error") {
    val result = ArgParser.parse(Array("stake", "remove"))
    assert(result.isLeft)
  }

  test("vote <daoKey> <proposalIndex> <v1,v2,...>") {
    val result = ArgParser.parse(Array("vote", "abc123", "3", "100,0,50"))
    assert(result.isRight)
    result.foreach { cliArgs =>
      cliArgs.command match {
        case Command.Vote(daoKey, index, votes) =>
          assert(daoKey == "abc123")
          assert(index == 3)
          assert(votes == List(100L, 0L, 50L))
        case other => fail(s"expected a Vote command, got $other")
      }
    }
  }

  test("vote with a non-integer proposal index is an error") {
    val result = ArgParser.parse(Array("vote", "abc123", "not-a-number", "100,0"))
    assert(result.isLeft)
    assert(result.left.get.contains("not-a-number"))
  }

  test("vote with a non-integer vote allocation is an error") {
    val result = ArgParser.parse(Array("vote", "abc123", "0", "100,abc"))
    assert(result.isLeft)
  }

  // M3
  test("M3: vote with a negative allocation element is rejected at parse time") {
    val result = ArgParser.parse(Array("vote", "abc123", "0", "100,-5"))
    assert(result.isLeft)
    assert(result.left.get.toLowerCase.contains("negative"))
  }

  test("--address is repeatable, first one wins as change/receive address") {
    val result = ArgParser.parse(
      Array("--address", "addr1", "--address", "addr2", "stake", "status", "abc123")
    )
    assert(
      result == Right(
        CliArgs(
          None,
          None,
          None,
          Command.StakeStatus("abc123"),
          addresses = List("addr1", "addr2")
        )
      )
    )
  }

  test("--address without a value is an error") {
    val result = ArgParser.parse(Array("--address"))
    assert(result == Left("--address requires a value"))
  }

  test("--no-sign is captured") {
    val result = ArgParser.parse(
      Array("--address", "addr1", "--no-sign", "stake", "add", "abc123", "1000")
    )
    assert(
      result == Right(
        CliArgs(
          None,
          None,
          None,
          Command.StakeAdd("abc123", 1000L),
          addresses = List("addr1"),
          noSign    = true
        )
      )
    )
  }

  test("--port overrides the default signing server port") {
    val result =
      ArgParser.parse(
        Array("--address", "addr1", "--port", "9999", "stake", "status", "abc")
      )
    assert(
      result == Right(
        CliArgs(
          None,
          None,
          None,
          Command.StakeStatus("abc"),
          addresses = List("addr1"),
          port      = Some(9999)
        )
      )
    )
  }

  test("--port without a value is an error") {
    val result = ArgParser.parse(Array("--port"))
    assert(result == Left("--port requires a value"))
  }

  test("--port with a non-integer value is an error") {
    val result = ArgParser.parse(Array("--port", "not-a-port", "sync"))
    assert(result.isLeft)
    assert(result.left.get.contains("not-a-port"))
  }

  // m10
  test("--port out of range (0, negative, or > 65535) is an error") {
    assert(ArgParser.parse(Array("--port", "0", "sync")).isLeft)
    assert(ArgParser.parse(Array("--port", "-1", "sync")).isLeft)
    assert(ArgParser.parse(Array("--port", "65536", "sync")).isLeft)
    assert(ArgParser.parse(Array("--port", "65535", "sync")).isRight)
    assert(ArgParser.parse(Array("--port", "1", "sync")).isRight)
  }

  // M5/m12: --stake-key is a single global flag now (not parsed per-command), since
  // stake add, stake remove and vote all accept it.
  test("--stake-key is a global flag usable before stake add") {
    val result = ArgParser.parse(
      Array(
        "--address",
        "addr1",
        "--stake-key",
        "deadbeef",
        "stake",
        "add",
        "abc123",
        "1000"
      )
    )
    assert(
      result == Right(
        CliArgs(
          None,
          None,
          None,
          Command.StakeAdd("abc123", 1000L),
          addresses        = List("addr1"),
          stakeKeyOverride = Some("deadbeef")
        )
      )
    )
  }

  test("--stake-key is usable before stake remove") {
    val result = ArgParser.parse(
      Array("--address", "addr1", "--stake-key", "deadbeef", "stake", "remove", "abc123")
    )
    assert(
      result == Right(
        CliArgs(
          None,
          None,
          None,
          Command.StakeRemove("abc123"),
          addresses        = List("addr1"),
          stakeKeyOverride = Some("deadbeef")
        )
      )
    )
  }

  test("--stake-key is usable before vote") {
    val result = ArgParser.parse(
      Array(
        "--address",
        "addr1",
        "--stake-key",
        "deadbeef",
        "vote",
        "abc123",
        "0",
        "100,0"
      )
    )
    assert(result.isRight)
    assert(result.toOption.get.stakeKeyOverride == Some("deadbeef"))
  }

  test("--stake-key without a value is an error") {
    val result = ArgParser.parse(Array("--stake-key"))
    assert(result == Left("--stake-key requires a value"))
  }

  // m12: --stake-key placed AFTER the command (the old per-command syntax) is now a
  // "flags before the command" parse error, not a supported shape.
  test(
    "m12: --stake-key after the command is rejected as misplaced, not silently accepted"
  ) {
    val result =
      ArgParser.parse(Array("stake", "add", "abc123", "1000", "--stake-key", "deadbeef"))
    assert(result.isLeft)
    assert(result.left.get.contains("flags must come before the command"))
  }

  test(
    "m12: a stray --flag anywhere in the command's own arguments is rejected the same way"
  ) {
    val result = ArgParser.parse(Array("vote", "abc123", "0", "100,0", "--bogus"))
    assert(result.isLeft)
    assert(result.left.get.contains("flags must come before the command"))
  }

  // m7
  test("--host overrides the advertised signing host") {
    val result = ArgParser.parse(
      Array("--address", "addr1", "--host", "203.0.113.5", "stake", "status", "abc")
    )
    assert(
      result == Right(
        CliArgs(
          None,
          None,
          None,
          Command.StakeStatus("abc"),
          addresses = List("addr1"),
          host      = Some("203.0.113.5")
        )
      )
    )
  }

  test("--host without a value is an error") {
    val result = ArgParser.parse(Array("--host"))
    assert(result == Left("--host requires a value"))
  }
}
