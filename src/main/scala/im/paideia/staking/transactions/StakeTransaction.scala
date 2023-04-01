package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import im.paideia.DAOConfig
import sigmastate.Values
import org.ergoplatform.appkit.Address
import special.collection.Coll
import im.paideia.staking._
import im.paideia.staking.boxes._
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.DAO
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.common.filtering._
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ContextVar
import scorex.crypto.authds.ADDigest

case class StakeTransaction(
  _ctx: BlockchainContextImpl,
  stakeProxyInput: InputBox,
  _changeAddress: ErgoAddress,
  daoKey: String
) extends PaideiaTransaction {

  ctx = _ctx
  val amount = stakeProxyInput.getRegisters().get(1).getValue().asInstanceOf[Long]
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

  val contextVars = stakeStateInputBox
    .stake(stakeKey, amount)
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

  val proxyContextVars = List(
    ContextVar.of(
      0.toByte,
      config.getProof(
        ConfKeys.im_paideia_staking_state_tokenid,
        ConfKeys.im_paideia_dao_name
      )(Some(configDigest))
    ),
    ContextVar.of(1.toByte, contextVars(2).getValue()),
    ContextVar.of(2.toByte, contextVars(3).getValue())
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
      Address
        .fromPropositionBytes(
          ctx.getNetworkType(),
          stakeProxyInput
            .getRegisters()
            .get(0)
            .getValue()
            .asInstanceOf[Coll[Byte]]
            .toArray
        )
        .toErgoContract()
    )
    .build()

  fee = 1500000L
  inputs = List[InputBox](
    stakeStateInput.withContextVars(contextVars: _*),
    stakeProxyInput.withContextVars(proxyContextVars: _*)
  )
  dataInputs    = List[InputBox](configInput)
  outputs       = List[OutBox](stakeStateInputBox.outBox, userOutput)
  changeAddress = _changeAddress
}
