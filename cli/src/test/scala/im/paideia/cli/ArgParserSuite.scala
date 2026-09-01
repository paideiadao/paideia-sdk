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
}
