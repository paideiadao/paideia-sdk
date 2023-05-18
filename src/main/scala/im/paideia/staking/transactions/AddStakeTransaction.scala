package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.ErgoToken
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.ErgoValue
import im.paideia.staking.contracts.StakeState
import im.paideia.staking._
import im.paideia.staking.contracts._
import im.paideia.staking.boxes._
import im.paideia.DAO
import sigmastate.Values
import org.ergoplatform.appkit.Address
import im.paideia.common.filtering.FilterType
import im.paideia.Paideia
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.CompareField
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import im.paideia.common.contracts.PaideiaContractSignature
import special.collection.Coll
import scorex.crypto.authds.ADDigest

case class AddStakeTransaction(
  _ctx: BlockchainContextImpl,
  addStakeProxyInput: InputBox,
  _changeAddress: ErgoAddress,
  daoKey: String
) extends PaideiaTransaction {
  val stakingKey = addStakeProxyInput.getTokens().get(0).getId().toString()
  val amount     = addStakeProxyInput.getRegisters().get(1).getValue().asInstanceOf[Long]
  val config     = Paideia.getConfig(daoKey)

  val state = TotalStakingState(daoKey)

  val stakeStateInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid))
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val stakeStateInputBox = StakeStateBox.fromInputBox(_ctx, stakeStateInput)

  val configInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      daoKey,
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

  if (!configDigest.sameElements(config._config.digest))
    throw new Exception("Config not synced correctly")

  val stakingContextVars = stakeStateInputBox
    .addStake(stakingKey, amount)

  val contextVars = stakingContextVars.stakingStateContextVars
    .::(
      ContextVar.of(
        0.toByte,
        stakeStateInputBox.useContract.getConfigContext(Some(configDigest))
      )
    )

  val changeStakeContract = ChangeStake(
    config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking_changestake)
      .withDaoKey(daoKey)
  )
  val changeStakeInput =
    changeStakeContract.boxes(changeStakeContract.getUtxoSet.toArray.apply(0))

  val changeStakeContextVars = stakingContextVars.companionContextVars
    .::(
      ContextVar.of(
        0.toByte,
        changeStakeContract.getConfigContext(Some(configDigest))
      )
    )

  val proxyContextVars = List(
    ContextVar.of(
      0.toByte,
      config.getProof(
        ConfKeys.im_paideia_staking_state_tokenid
      )(Some(configDigest))
    ),
    ContextVar.of(1.toByte, stakingContextVars.companionContextVars(0).getValue()),
    ContextVar.of(2.toByte, stakingContextVars.companionContextVars(1).getValue())
  )

  val userOutput = _ctx
    .newTxBuilder()
    .outBoxBuilder()
    .value(1000000L)
    .tokens(
      new ErgoToken(stakingKey, 1L)
    )
    .contract(
      Address
        .fromPropositionBytes(
          _ctx.getNetworkType(),
          addStakeProxyInput
            .getRegisters()
            .get(0)
            .getValue()
            .asInstanceOf[Coll[Byte]]
            .toArray
        )
        .toErgoContract
    )
    .build()

  ctx           = _ctx
  fee           = 1500000
  changeAddress = _changeAddress
  inputs = List[InputBox](
    stakeStateInput.withContextVars(contextVars: _*),
    changeStakeInput.withContextVars(changeStakeContextVars: _*),
    addStakeProxyInput.withContextVars(proxyContextVars: _*)
  )
  dataInputs = List[InputBox](configInput)
  outputs = List[OutBox](
    stakeStateInputBox.outBox,
    changeStakeContract.box(ctx).outBox,
    userOutput
  )

}
