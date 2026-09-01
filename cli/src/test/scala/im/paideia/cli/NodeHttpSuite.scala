package im.paideia.cli

import org.scalatest.funsuite.AnyFunSuite

/** Offline coverage of [[NodeHttp.eip12ToNodeJson]] - the EIP-12 → node-JSON rewrite that
  * turns a wallet's string-typed `value`/`amount` fields into the JSON numbers the node's
  * circe decoders require.
  */
class NodeHttpSuite extends AnyFunSuite {

  test("eip12ToNodeJson converts value/amount strings to numbers, everywhere") {
    // Trimmed-down EIP-12 SignedTransaction shape: string `value`/`amount` in outputs,
    // an amount above 2^53 (the reason EIP-12 uses strings at all), and untouched
    // string fields (`boxId`, register hex) that must stay strings.
    val eip12 =
      """{"id":"aa","inputs":[{"boxId":"bb","spendingProof":{"proofBytes":"cc","extension":{"0":"dd"}}}],
        |"dataInputs":[{"boxId":"ee"}],
        |"outputs":[{"boxId":"ff","value":"3500000","ergoTree":"0008cd","creationHeight":1,
        |"assets":[{"tokenId":"11","amount":"9007199254740993"}],
        |"additionalRegisters":{"R4":"0e0102"}}]}""".stripMargin.replace("\n", "")

    val out = NodeHttp.eip12ToNodeJson(eip12)

    assert(out.contains(""""value":3500000"""))
    assert(out.contains(""""amount":9007199254740993"""))
    assert(!out.contains(""""value":"3500000""""))
    assert(!out.contains(""""amount":"9007199254740993""""))
    assert(out.contains(""""boxId":"ff""""))
    assert(out.contains(""""R4":"0e0102""""))
    assert(out.contains(""""proofBytes":"cc""""))
  }

  test("eip12ToNodeJson leaves already-numeric values untouched") {
    val json = """{"outputs":[{"value":1000,"assets":[{"amount":5}]}]}"""
    val out  = NodeHttp.eip12ToNodeJson(json)
    assert(out.contains(""""value":1000"""))
    assert(out.contains(""""amount":5"""))
  }
}
