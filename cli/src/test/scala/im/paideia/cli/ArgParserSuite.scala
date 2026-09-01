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
      result == Right(CliArgs(None, None, None, Command.StakeAdd("abc123", 1000L, None)))
    )
  }

  test("stake add <daoKey> <amount> --stake-key <id>") {
    val result =
      ArgParser.parse(Array("stake", "add", "abc123", "1000", "--stake-key", "deadbeef"))
    assert(
      result == Right(
        CliArgs(None, None, None, Command.StakeAdd("abc123", 1000L, Some("deadbeef")))
      )
    )
  }

  test("stake add with a non-integer amount is an error") {
    val result = ArgParser.parse(Array("stake", "add", "abc123", "not-a-number"))
    assert(result.isLeft)
    assert(result.left.get.contains("not-a-number"))
  }

  test("stake remove <daoKey> <amount>") {
    val result = ArgParser.parse(Array("stake", "remove", "abc123", "500"))
    assert(
      result == Right(
        CliArgs(None, None, None, Command.StakeRemove("abc123", RemoveAmount.Exact(500L)))
      )
    )
  }

  test("stake remove <daoKey> all") {
    val result = ArgParser.parse(Array("stake", "remove", "abc123", "all"))
    assert(
      result == Right(
        CliArgs(None, None, None, Command.StakeRemove("abc123", RemoveAmount.All))
      )
    )
  }

  test("stake remove with a non-positive amount is an error") {
    val result = ArgParser.parse(Array("stake", "remove", "abc123", "0"))
    assert(result.isLeft)
    val negative = ArgParser.parse(Array("stake", "remove", "abc123", "-5"))
    assert(negative.isLeft)
  }

  test("stake remove with a non-integer, non-'all' amount is an error") {
    val result = ArgParser.parse(Array("stake", "remove", "abc123", "lots"))
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
          Command.StakeAdd("abc123", 1000L, None),
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
}
