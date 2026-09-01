package im.paideia.app

import com.google.gson.JsonParser
import im.paideia.common.PaideiaTestSuite
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.staking.StakingTest
import im.paideia.staking.contracts.Stake
import im.paideia.staking.contracts.StakeState
import im.paideia.util.ConfKeys
import im.paideia.util.Util
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.sdk.ErgoToken

/** Golden-shape coverage for [[Eip12UnsignedTx]]: builds a real unsigned tx (a first-time
  * `StakeTransaction`, via [[UserTransactions.stake]] and a stub [[UserBoxSelector]] -
  * the same fixture `UserTransactionsSuite` uses) and checks the exact EIP-12 field names
  * and types `Eip12UnsignedTx.toJsonObject` produces - the shape a wallet's
  * `ergo.sign_tx` (Nautilus) actually parses, ported from paideia-state's
  * `models.MUnsignedTransaction`.
  */
class Eip12Suite extends PaideiaTestSuite {

  private val walletAddressStr = "4MQyML64GnzMxZgm"
  private val walletAddress    = Address.create(walletAddressStr)

  private def fundingBox(
    ctx: BlockchainContextImpl,
    value: Long,
    tokens: List[ErgoToken] = Nil
  ): InputBox = {
    var b = ctx
      .newTxBuilder()
      .outBoxBuilder()
      .contract(walletAddress.toErgoContract())
      .value(value)
    if (tokens.nonEmpty) b = b.tokens(tokens: _*)
    b.build().convertToInputWith(Util.randomKey, 0.toShort)
  }

  test("Eip12UnsignedTx has the exact field shape ergo.sign_tx expects") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)

        val dao             = StakingTest.testDAO
        val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
        dao.config.set(
          ConfKeys.im_paideia_contracts_staking_state,
          stakingContract.contractSignature
        )
        val stakingState = stakingContract.emptyBox(ctx, dao, 100000000L)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        dao.config
          .set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

        // StakeTransaction spends the Stake contract's own single utility box as its
        // second input (alongside the StakeState box) - see StakeTransactionSuite.
        val stakeContract = Stake(PaideiaContractSignature(daoKey = dao.key))
        stakeContract.newBox(stakeContract.box(ctx, 1000000L).inputBox(), false)

        stakingContract.clearBoxes()
        stakingContract.newBox(stakingState.inputBox(), false)

        // StakeTransaction requires the 1,000 raw units being staked to come from the
        // wallet too (StakeStateBox.stake increases stakedTokenTotal by that amount),
        // not just ERG headroom - see UserTransactionsSuite's daoTokenIdOf for the same
        // requirement.
        val daoTokenId =
          new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid))
            .toString()
        val selector = new UserBoxSelector(_ =>
          List(fundingBox(ctx, 5000000L, List(new ErgoToken(daoTokenId, 1000L))))
        )
        val tx = UserTransactions.stake(
          ctx,
          selector,
          dao.key,
          1000L,
          List(walletAddressStr),
          walletAddressStr
        )

        val unsigned = tx.unsigned()
        val eip12    = Eip12UnsignedTx(unsigned)

        // --- structural shape ---
        assert(eip12.inputs.nonEmpty, "expected at least one input")
        assert(eip12.outputs.nonEmpty, "expected at least one output")
        eip12.inputs.foreach { inp =>
          assert(inp.boxId.nonEmpty)
          assert(inp.value.nonEmpty)
          assert(
            scala.util.Try(BigInt(inp.value)).isSuccess,
            s"value '${inp.value}' should be a decimal string"
          )
          assert(inp.ergoTree.nonEmpty)
          assert(inp.transactionId.nonEmpty)
          assert(inp.index >= 0)
          inp.assets.foreach { a =>
            assert(a.tokenId.nonEmpty)
            assert(
              scala.util.Try(BigInt(a.amount)).isSuccess,
              s"amount '${a.amount}' should be a decimal string"
            )
          }
          inp.additionalRegisters.keys.foreach(k => assert(k.matches("R[4-9]")))
        }
        eip12.outputs.foreach { outp =>
          assert(outp.value.nonEmpty)
          assert(scala.util.Try(BigInt(outp.value)).isSuccess)
          assert(outp.ergoTree.nonEmpty)
        }

        // --- JSON shape (what actually reaches the wallet) ---
        val json = Eip12UnsignedTx.toJson(eip12)
        val root = new JsonParser().parse(json).getAsJsonObject
        assert(root.has("inputs"))
        assert(root.has("dataInputs"))
        assert(root.has("outputs"))
        assert(root.get("inputs").getAsJsonArray.size() == eip12.inputs.size)
        assert(root.get("dataInputs").getAsJsonArray.size() == eip12.dataInputs.size)
        assert(root.get("outputs").getAsJsonArray.size() == eip12.outputs.size)

        val firstInputJson = root.get("inputs").getAsJsonArray.get(0).getAsJsonObject
        Seq(
          "boxId",
          "value",
          "ergoTree",
          "assets",
          "additionalRegisters",
          "creationHeight",
          "transactionId",
          "index",
          "extension"
        ).foreach(field =>
          assert(
            firstInputJson.has(field),
            s"input JSON missing field '$field': $firstInputJson"
          )
        )
        // value/index/creationHeight must be JSON strings/numbers of the right kind, not
        // e.g. an object - box `value` in particular must be a STRING (nanoERG amounts
        // routinely exceed Number.MAX_SAFE_INTEGER).
        assert(
          firstInputJson
            .get("value")
            .isJsonPrimitive && firstInputJson.get("value").getAsJsonPrimitive.isString
        )
        assert(
          firstInputJson
            .get("index")
            .isJsonPrimitive && firstInputJson.get("index").getAsJsonPrimitive.isNumber
        )

        val firstOutputJson = root.get("outputs").getAsJsonArray.get(0).getAsJsonObject
        Seq("value", "ergoTree", "assets", "additionalRegisters", "creationHeight")
          .foreach(field =>
            assert(
              firstOutputJson.has(field),
              s"output JSON missing field '$field': $firstOutputJson"
            )
          )
        assert(
          !firstOutputJson.has("boxId"),
          "an output has no boxId yet - unlike an input"
        )
      }
    })
  }
}
