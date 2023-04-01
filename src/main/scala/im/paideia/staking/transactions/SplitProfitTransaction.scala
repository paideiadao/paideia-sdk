package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.impl.ErgoTreeContract
import im.paideia.DAOConfig
import special.collection.Coll
import org.ergoplatform.appkit.ErgoToken
import scala.collection.JavaConverters._
import im.paideia.staking._
import im.paideia.staking.boxes._
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.util.ConfKeys
import im.paideia.common.filtering.CompareField
import im.paideia.common.contracts.Treasury
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.appkit.commands.ErgoIdPType
import org.ergoplatform.appkit.ContextVar
import scorex.crypto.authds.ADDigest

case class SplitProfitTransaction() extends PaideiaTransaction {}

object SplitProfitTransaction {

  def apply(
    ctx: BlockchainContextImpl,
    splitProfitInputs: List[InputBox],
    dao: DAO
  ): SplitProfitTransaction = {
    val profitSharePct: Byte = dao.config(ConfKeys.im_paideia_staking_profit_share_pct)

    val treasuryContract = Treasury(PaideiaContractSignature(daoKey = dao.key))

    val totalErg = splitProfitInputs.foldLeft(0L)((z, spi) => z + spi.getValue()) - 2000000L

    val configInput = Paideia.getBox(
      new FilterLeaf[String](
        FilterType.FTEQ,
        dao.key,
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

    val splitProfitContext = ContextVar.of(
      0.toByte,
      dao.config.getProof(
        ConfKeys.im_paideia_contracts_treasury,
        ConfKeys.im_paideia_contracts_staking,
        ConfKeys.im_paideia_staking_profit_share_pct,
        ConfKeys.im_paideia_dao_tokenid,
        ConfKeys.im_paideia_staking_profit_tokenids
      )(Some(configDigest))
    )

    if (profitSharePct > 0) {

      val stakeStateInput = Paideia.getBox(
        new FilterLeaf[String](
          FilterType.FTEQ,
          new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid))
            .toString(),
          CompareField.ASSET,
          0
        )
      )(0)

      val stakeStateInputBox = StakeStateBox.fromInputBox(ctx, stakeStateInput)

      val whiteListedTokens = dao.config
        .getArray[Object](ConfKeys.im_paideia_staking_profit_tokenids)
        .map((arrB: Object) =>
          new ErgoId(arrB.asInstanceOf[Array[Object]].map(_.asInstanceOf[Byte]))
        )

      val profitToShare = Array.fill(whiteListedTokens.size + 2)(0L)

      val govToken = new ErgoId(
        dao.config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid)
      )

      profitToShare(0) = (splitProfitInputs.foldLeft(0L)((z, spi) =>
          z + spi
            .getTokens()
            .asScala
            .foldLeft(0L)((x, et) =>
              if (et.getId() == govToken)
                x + et.getValue()
              else
                x
            )
        ) * profitSharePct) / 100
      profitToShare(1) = (totalErg * profitSharePct) / 100
      whiteListedTokens.indices.foreach(i =>
        profitToShare(i + 2) = (splitProfitInputs.foldLeft(0L)((z, spi) =>
            z + spi
              .getTokens()
              .asScala
              .foldLeft(0L)((x, et) =>
                if (et.getId() == whiteListedTokens(i)) x + et.getValue()
                else x
              )
          ) * profitSharePct) / 100
      )

      val extraTokens = stakeStateInput
        .getTokens()
        .subList(2, stakeStateInput.getTokens().size)
        .asScala
        .map((token: ErgoToken) =>
          whiteListedTokens.find(_ == token.getId()) match {
            case None => token
            case Some(value) =>
              new ErgoToken(
                token.getId(),
                token.getValue() + profitToShare(whiteListedTokens.indexOf(value) + 2)
              )
          }
        )

      val newExtraTokens = whiteListedTokens
        .filter((tokenId: ErgoId) =>
          stakeStateInput
            .getTokens()
            .asScala
            .find((t: ErgoToken) => t.getId() == tokenId) match {
            case None        => profitToShare(2 + whiteListedTokens.indexOf(tokenId)) > 0L
            case Some(value) => false
          }
        )
        .map(tId => new ErgoToken(tId, profitToShare(2 + whiteListedTokens.indexOf(tId))))

      val state = TotalStakingState(dao.key)

      val contextVars = stakeStateInputBox
        .profitShare(profitToShare.toList, extraTokens.toList ++ newExtraTokens.toList)
        .::(
          ContextVar.of(
            0.toByte,
            dao.config.getProof(
              ConfKeys.im_paideia_staking_emission_amount,
              ConfKeys.im_paideia_staking_emission_delay,
              ConfKeys.im_paideia_staking_cyclelength,
              ConfKeys.im_paideia_staking_profit_tokenids,
              ConfKeys.im_paideia_staking_profit_thresholds,
              ConfKeys.im_paideia_contracts_staking
            )(Some(configDigest))
          )
        )

      val res = new SplitProfitTransaction()
      res.ctx           = ctx
      res.changeAddress = treasuryContract.contract.toAddress().getErgoAddress()
      res.fee           = 1500000
      res.inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*)) ++ splitProfitInputs
          .map(_.withContextVars(splitProfitContext))
      res.dataInputs = List[InputBox](configInput)
      res.outputs    = List[OutBox](stakeStateInputBox.outBox)
      res

    } else {

      val res = new SplitProfitTransaction()
      res.ctx           = ctx
      res.changeAddress = treasuryContract.contract.toAddress().getErgoAddress()
      res.fee           = 1000000
      res.inputs        = splitProfitInputs.map(_.withContextVars(splitProfitContext))
      res.dataInputs    = List[InputBox](configInput)
      res.outputs       = List[OutBox]()
      res

    }
  }
}
