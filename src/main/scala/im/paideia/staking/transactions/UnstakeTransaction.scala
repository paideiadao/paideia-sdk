package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.sdk.ErgoToken
import im.paideia.DAOConfig
import im.paideia.staking._
import im.paideia.staking.boxes._
import im.paideia.DAO
import im.paideia.util.ConfKeys
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.common.filtering._
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.Address
import sigma.Coll
import scala.collection.JavaConverters._
import scorex.crypto.authds.ADDigest
import im.paideia.staking.contracts.Unstake
import im.paideia.staking.contracts.ChangeStake
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigma.Colls
import im.paideia.util.TxTypes

case class UnstakeTransaction(
  _ctx: BlockchainContextImpl,
  stakingKey: String,
  newStakeAndProfitRecord: Coll[Byte],
  _changeAddress: Address,
  userAddress: Address,
  daoKey: String,
  var stakingContextVars: StakingContextVars
) extends PaideiaTransaction {

  ctx = _ctx

  val config = Paideia.getConfig(daoKey)

  val state = TotalStakingState(daoKey)

  val newStakeRecord: StakeRecord = StakeRecord.stakeRecordConversion.convertFromBytes(
    newStakeAndProfitRecord.toArray
  )

  val whiteListedTokens = config
    .getArray[Object](ConfKeys.im_paideia_staking_profit_tokenids)
    .map((arrB: Object) =>
      new ErgoId(arrB.asInstanceOf[Array[Object]].map(_.asInstanceOf[Byte]))
    )

  val stakeStateInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid))
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val stakeStateInputBox = StakeStateBox.fromInputBox(ctx, stakeStateInput)

  val currentStakeRecord =
    stakeStateInputBox.getStake(stakingKey)

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

  val newExtraTokens = stakeStateInputBox.extraTokens
    .map { (et: ErgoToken) =>
      val profitRecordIndex = whiteListedTokens.indexOf(et.getId)
      new ErgoToken(
        et.getId,
        et.getValue - (currentStakeRecord
          .rewards(1 + profitRecordIndex) - newStakeRecord.rewards(
          1 + profitRecordIndex
        ))
      )
    }
    .filter((et: ErgoToken) => et.getValue > 0L)
    .toList

  stakingContextVars = stakeStateInputBox
    .unstake(stakingKey, newStakeRecord, newExtraTokens)

  val contextVars = stakingContextVars.stakingStateContextVars
    .::(
      ContextVar.of(
        1.toByte,
        if (
          stakingContextVars
            .stakingStateContextVars(0)
            .getValue
            .getValue
            .equals(TxTypes.UNSTAKE.getValue())
        )
          stakeStateInputBox.useContract.getConfigContext(
            Some(configDigest),
            ConfKeys.im_paideia_contracts_staking_unstake
          )
        else
          stakeStateInputBox.useContract.getConfigContext(
            Some(configDigest),
            ConfKeys.im_paideia_contracts_staking_changestake
          )
      )
    )

  val companionContract =
    if (contextVars(1).getValue.getValue.equals(TxTypes.UNSTAKE.getValue()))
      Unstake(
        config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking_unstake)
          .withDaoKey(daoKey)
      )
    else
      ChangeStake(
        config[PaideiaContractSignature](
          ConfKeys.im_paideia_contracts_staking_changestake
        ).withDaoKey(daoKey)
      )
  val companionOutput =
    if (contextVars(1).getValue.getValue.equals(TxTypes.UNSTAKE.getValue()))
      Unstake(
        config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking_unstake)
          .withDaoKey(daoKey)
      ).box(ctx).outBox
    else
      ChangeStake(
        config[PaideiaContractSignature](
          ConfKeys.im_paideia_contracts_staking_changestake
        ).withDaoKey(daoKey)
      )
        .box(ctx)
        .outBox

  val unstakeInput =
    companionContract.boxes(companionContract.getUtxoSet.toArray.apply(0))

  val unstakeContextVars = stakingContextVars.companionContextVars
    .::(
      ContextVar.of(
        0.toByte,
        companionContract.getConfigContext(Some(configDigest))
      )
    )

  val govTokenUnstake = if (currentStakeRecord.stake > newStakeRecord.stake) {
    List(
      new ErgoToken(
        config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),
        currentStakeRecord.stake - newStakeRecord.stake
      )
    )
  } else {
    List[ErgoToken]()
  }

  val profitTokenUnstake = currentStakeRecord.rewards.indices
    .slice(0, currentStakeRecord.rewards.size - 1)
    .map((i: Int) =>
      if (currentStakeRecord.rewards(i + 1) - newStakeRecord.rewards(i + 1) > 0) {
        Some(
          new ErgoToken(
            whiteListedTokens(i),
            currentStakeRecord.rewards(i + 1) - newStakeRecord.rewards(i + 1)
          )
        )
      } else {
        None
      }
    )
    .flatten
    .toList

  val stakeKeyReturned =
    if (contextVars(1).getValue.getValue != TxTypes.UNSTAKE.getValue())
      List[ErgoToken](new ErgoToken(stakingKey, 1L))
    else
      List[ErgoToken]()

  tokensToBurn =
    if (contextVars(1).getValue.getValue == TxTypes.UNSTAKE.getValue())
      List[ErgoToken](new ErgoToken(stakingKey, 1L))
    else
      List[ErgoToken]()

  val tokens = stakeKeyReturned ++ govTokenUnstake ++ profitTokenUnstake

  val userOutput = ctx
    .newTxBuilder()
    .outBoxBuilder()
    .value(1000000L + currentStakeRecord.rewards(0) - newStakeRecord.rewards(0))
    .tokens(
      tokens: _*
    )
    .contract(
      userAddress.toErgoContract
    )
    .build()

  changeAddress = _changeAddress
  fee           = 2350000L
  inputs = List[InputBox](
    stakeStateInput.withContextVars(contextVars: _*),
    unstakeInput.withContextVars(unstakeContextVars: _*)
  )
  dataInputs = List[InputBox](configInput)
  outputs    = List[OutBox](stakeStateInputBox.outBox, companionOutput, userOutput)
}
