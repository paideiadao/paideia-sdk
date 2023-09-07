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
import im.paideia.staking.contracts.SplitProfit
import im.paideia.util.ConfKeys
import im.paideia.util.Env
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.sdk.ErgoToken
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.ErgoTreeContract
import special.sigma.AvlTree
import scorex.crypto.authds.ADDigest
import im.paideia.staking.contracts.StakeSnapshot

case class EmitTransaction(
  _ctx: BlockchainContextImpl,
  stakeStateInput: InputBox,
  operatorAddress: ErgoAddress,
  daoKey: String
) extends PaideiaTransaction {
  ctx = _ctx

  val config = Paideia.getConfig(daoKey)

  val state = TotalStakingState(daoKey)

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

  val treasuryContract = Treasury(PaideiaContractSignature(daoKey = daoKey))

  val maxMinerOperatorErg: Long = paideiaConfig(ConfKeys.im_paideia_fees_operator_max_erg)

  val treasuryAddress = treasuryContract.contract.toAddress()

  val coveringTreasuryBoxes = treasuryContract
    .findBoxes(
      paideiaConfig[Long](ConfKeys.im_paideia_fees_operator_max_erg) + 1000000L,
      Array(
        new ErgoToken(
          Env.paideiaTokenId,
          paideiaConfig[Long](ConfKeys.im_paideia_fees_emit_operator_paideia) +
            (paideiaConfig[Long](
              ConfKeys.im_paideia_fees_emit_paideia
            ) * stakeStateInputBox.state.currentStakingState
              .size(Some(stakeStateInputBox.stateDigest)) + 1)
        )
      )
    )
    .get

  val tokensInPool = stakeStateInput
    .getTokens()
    .get(1)
    .getValue - (stakeStateInputBox.state.currentStakingState
    .totalStaked(Some(stakeStateInputBox.stateDigest)) + 1L)

  val stakingContextVars = stakeStateInputBox
    .emit(
      ctx.createPreHeader().build().getTimestamp(),
      tokensInPool
    )

  val contextVars = stakingContextVars.stakingStateContextVars
    .::(
      ContextVar.of(
        0.toByte,
        stakeStateInputBox.useContract.getConfigContext(Some(configDigest))
      )
    )

  val snapshotContract = StakeSnapshot(
    config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking_compound)
      .withDaoKey(daoKey)
  )
  val snapshotInput =
    snapshotContract.boxes(snapshotContract.getUtxoSet.toArray.apply(0))

  val snapshotContextVars = stakingContextVars.companionContextVars
    .::(
      ContextVar.of(
        0.toByte,
        snapshotContract.getConfigContext(Some(configDigest))
      )
    )

  val operatorOutput = ctx
    .newTxBuilder()
    .outBoxBuilder()
    .contract(new ErgoTreeContract(operatorAddress.script, ctx.getNetworkType()))
    .value(150000L)
    .tokens(
      new ErgoToken(
        Env.paideiaTokenId,
        paideiaConfig[Long](ConfKeys.im_paideia_fees_emit_operator_paideia)
      )
    )
    .build()

  val paideiaSplitProfitContractSignature =
    paideiaConfig[PaideiaContractSignature](ConfKeys.im_paideia_contracts_split_profit)
      .withDaoKey(Env.paideiaDaoKey)

  val paideiaSplitProfitContract = SplitProfit(
    paideiaSplitProfitContractSignature
  )

  val paideiaSplitProfitOutput = ctx
    .newTxBuilder()
    .outBoxBuilder()
    .contract(paideiaSplitProfitContract.contract)
    .value(1000000L)
    .tokens(
      new ErgoToken(
        Env.paideiaTokenId,
        (paideiaConfig[Long](
          ConfKeys.im_paideia_fees_emit_paideia
        ) * stakeStateInputBox.state.currentStakingState
          .size(Some(stakeStateInputBox.stateDigest)) + 1L)
      )
    )
    .build()

  val treasuryContextVars = List(
    ContextVar.of(
      0.toByte,
      paideiaConfig.getProof(
        ConfKeys.im_paideia_fees_emit_paideia,
        ConfKeys.im_paideia_fees_emit_operator_paideia,
        ConfKeys.im_paideia_contracts_split_profit,
        ConfKeys.im_paideia_fees_operator_max_erg
      )(Some(paideiaConfigDigest))
    ),
    ContextVar.of(
      1.toByte,
      config.getProof(
        ConfKeys.im_paideia_contracts_staking_compound,
        ConfKeys.im_paideia_contracts_staking_snapshot
      )(Some(configDigest))
    )
  )

  changeAddress = treasuryAddress.getErgoAddress()
  fee           = maxMinerOperatorErg - 150000L - 1000000L
  inputs = List[InputBox](
    stakeStateInput.withContextVars(contextVars: _*),
    snapshotInput.withContextVars(snapshotContextVars: _*)
  ) ++ coveringTreasuryBoxes
    .map(_.withContextVars(treasuryContextVars: _*))
  dataInputs = List[InputBox](configInput, paideiaConfigInput)
  outputs = List[OutBox](
    stakeStateInputBox.outBox,
    snapshotContract.box(ctx).outBox,
    operatorOutput,
    paideiaSplitProfitOutput
  )
}
