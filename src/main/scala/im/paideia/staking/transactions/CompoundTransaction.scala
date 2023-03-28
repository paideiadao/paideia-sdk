package im.paideia.staking.transactions

import im.paideia.DAO
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Treasury
import im.paideia.common.filtering._
import im.paideia.common.transactions._
import im.paideia.staking._
import im.paideia.staking.boxes._
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.staking.contracts.SplitProfit
import im.paideia.util.ConfKeys
import im.paideia.util.Env
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.ErgoTreeContract
import special.sigma.AvlTree
import scorex.crypto.authds.ADDigest

case class CompoundTransaction(
  _ctx: BlockchainContextImpl,
  stakeStateInput: InputBox,
  operatorAddress: ErgoAddress,
  daoKey: String
) extends PaideiaTransaction {
  ctx = _ctx

  val config = Paideia.getConfig(daoKey)

  val state = TotalStakingState(daoKey)

  val treasuryContract = Treasury(PaideiaContractSignature(daoKey = daoKey))

  val treasuryAddress = treasuryContract.contract.toAddress()

  val stakeStateInputBox = StakeStateBox.fromInputBox(ctx, stakeStateInput)

  val configInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      daoKey,
      CompareField.ASSET,
      0
    )
  )(0)

  val paideiaConfigInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      Env.paideiaDaoKey,
      CompareField.ASSET,
      0
    )
  )(0)

  val configDigest =
    ADDigest @@ configInput
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

  val paideiaConfigDigest =
    ADDigest @@ paideiaConfigInput
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

  if (!configDigest.sameElements(config._config.digest))
    throw new Exception("Config not synced correctly")

  val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)

  val coveringTreasuryBoxes = treasuryContract
    .findBoxes(
      paideiaConfig[Long](ConfKeys.im_paideia_fees_operator_max_erg) + 1000000L,
      Array(
        new ErgoToken(
          Env.paideiaTokenId,
          paideiaConfig[Long](ConfKeys.im_paideia_fees_compound_operator_paideia)
        )
      )
    )
    .get

  val contextVars = stakeStateInputBox
    .compound(Env.compoundBatchSize)
    .::(
      ContextVar.of(
        0.toByte,
        config.getProof(
          ConfKeys.im_paideia_staking_emission_amount,
          ConfKeys.im_paideia_staking_emission_delay,
          ConfKeys.im_paideia_staking_cyclelength,
          ConfKeys.im_paideia_staking_profit_tokenids,
          ConfKeys.im_paideia_staking_profit_thresholds,
          ConfKeys.im_paideia_contracts_staking
        )(Some(configDigest))
      )
    )

  val operatorOutput = ctx
    .newTxBuilder()
    .outBoxBuilder()
    .contract(new ErgoTreeContract(operatorAddress.script, ctx.getNetworkType()))
    .value(1000000L)
    .tokens(
      new ErgoToken(
        Env.paideiaTokenId,
        paideiaConfig[Long](ConfKeys.im_paideia_fees_compound_operator_paideia)
      )
    )
    .build()

  val treasuryContextVars = List(
    ContextVar.of(
      0.toByte,
      paideiaConfig.getProof(
        ConfKeys.im_paideia_fees_compound_operator_paideia,
        ConfKeys.im_paideia_fees_operator_max_erg
      )(Some(paideiaConfigDigest))
    )
  )

  changeAddress = treasuryAddress.getErgoAddress()
  fee           = 1000000L
  inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*)) ++ coveringTreasuryBoxes
      .map(_.withContextVars(treasuryContextVars: _*))
  dataInputs = List[InputBox](configInput, paideiaConfigInput)
  outputs    = List[OutBox](stakeStateInputBox.outBox, operatorOutput)
}
