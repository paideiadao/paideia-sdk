package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import im.paideia.DAOConfig
import org.ergoplatform.appkit.Address
import sigma.Coll
import im.paideia.staking._
import im.paideia.staking.boxes._
import im.paideia.staking.contracts.Stake
import im.paideia.DAO
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.common.filtering._
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.appkit.ContextVar
import scorex.crypto.authds.ADDigest

case class StakeTransaction(
  _ctx: BlockchainContextImpl,
  amount: Long,
  userAddress: Address,
  _changeAddress: Address,
  daoKey: String
) extends PaideiaTransaction {

  ctx = _ctx
  val config = Paideia.getConfig(daoKey)

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

  val stakeStateInputBox = StakeStateBox.fromInputBox(ctx, stakeStateInput)

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
  val stakeKey = stakeStateInput.getId().toString()

  val stakingContextVars = stakeStateInputBox
    .stake(stakeKey, amount)

  val contextVars = stakingContextVars.stakingStateContextVars
    .::(
      ContextVar.of(
        1.toByte,
        stakeStateInputBox.useContract.getConfigContext(
          Some(configDigest),
          ConfKeys.im_paideia_contracts_staking_stake
        )
      )
    )

  val stakeContract = Stake(ConfKeys.im_paideia_contracts_staking_stake, daoKey)

  val stakeInput =
    stakeContract.boxes(stakeContract.getUtxoSet.toArray.apply(0))

  val stakeContextVars = stakingContextVars.companionContextVars
    .::(
      ContextVar.of(
        0.toByte,
        stakeContract.getConfigContext(Some(configDigest))
      )
    )

  val userOutput = ctx
    .newTxBuilder()
    .outBoxBuilder()
    .mintToken(
      new Eip4Token(
        stakeKey,
        1L,
        config[String](ConfKeys.im_paideia_dao_name) ++ " Stake Key",
        "Powered by Paideia",
        0
      )
    )
    .value(1000000L)
    .contract(
      userAddress
        .toErgoContract()
    )
    .build()

  fee = 2350000L
  inputs = List[InputBox](
    stakeStateInput.withContextVars(contextVars: _*),
    stakeInput.withContextVars(stakeContextVars: _*)
  )
  dataInputs = List[InputBox](configInput)
  outputs =
    List[OutBox](stakeStateInputBox.outBox, stakeContract.box(ctx).outBox, userOutput)
  changeAddress = _changeAddress
}
