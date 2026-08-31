package im.paideia.common.contracts

import org.ergoplatform.sdk.ErgoToken
import im.paideia.common.boxes.TreasuryBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import scala.collection.mutable.HashMap
import im.paideia.Paideia
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.InputBox
import im.paideia.util.Env
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import scala.collection.JavaConverters._
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import sigma.Colls
import sigma.ast.ConstantPlaceholder
import sigma.ast.SCollection
import sigma.ast.SByte
import im.paideia.common.events.{PaideiaEvent, PaideiaEventResponse}
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.transactions.ConsolidateTransaction
import scorex.util.encode.Base16
import sigma.ast.Tuple
import _root_.sigma.ast.CollectionConstant
import im.paideia.DAOConfigKey
import scorex.crypto.authds.ADDigest
import org.ergoplatform.appkit.Address

/** Treasury class represents the main contract for the Paideia Treasury which manages and
  * holds assets and tokens of the Paideia DAO treasury on Ergo Blockchain.
  *
  * @constructor
  *   creates a new instance of the Treasury class with given Paideia Contract Signature
  * @param contractSignature
  *   \- the signature of the Paideia Contract entity
  */
class Treasury(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(
    contractSignature,
    longLivingKey = ConfKeys.im_paideia_contracts_treasury.originalKey
  ) {

  /** Creates an instance of the TreasuryBox object.
    * @param ctx
    *   \- The context of the blockchain
    * @param daoConfig
    *   \- The current configuration of DAO
    * @param value
    *   \- Long type value of nanoERG.
    * @param tokens
    *   \- Tokens that are used within the TreasuryBox.
    */
  def box(
    ctx: BlockchainContextImpl,
    daoConfig: DAOConfig,
    value: Long,
    tokens: List[ErgoToken]
  ): TreasuryBox = {
    val res = new TreasuryBox
    res.ctx      = ctx
    res.contract = contract
    res.value    = value
    res.tokens   = tokens
    res
  }

  /** Constants for the Treasury contract are defined here. It can only contain objects
    * that were there during the compilation time (e.g literals).
    *
    * Currently it contains `_IM_PAIDEIA_DAO_ACTION_TOKENID` constant.
    */
  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_FEE_EMIT_PAIDEIA",
      ConfKeys.im_paideia_fees_emit_paideia.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_FEE_EMIT_OPERATOR_PAIDEIA",
      ConfKeys.im_paideia_fees_emit_operator_paideia.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_ACTION",
      Colls.fromArray(
        ConfKeys.im_paideia_contracts_action(Array[Byte]()).originalKeyBytes
      )
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT",
      ConfKeys.im_paideia_contracts_split_profit.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_FEE_OPERATOR_MAX_ERG",
      ConfKeys.im_paideia_fees_operator_max_erg.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_FEE_COMPOUND_OPERATOR_PAIDEIA",
      ConfKeys.im_paideia_fees_compound_operator_paideia.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_COMPOUND",
      ConfKeys.im_paideia_contracts_staking_compound.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT",
      ConfKeys.im_paideia_contracts_staking_snapshot.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_EMISSION",
      ConfKeys.im_paideia_staking_emission_amount.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_GOVERNANCE_TOKEN_ID",
      ConfKeys.im_paideia_dao_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_TREASURY",
      ConfKeys.im_paideia_contracts_treasury.ergoValue.getValue()
    )
    cons
  }

  override lazy val parameters: Map[String, Constant[SType]] = {
    val params = new scala.collection.mutable.HashMap[String, Constant[SType]]()
    params.put(
      "daoKeyId",
      ByteArrayConstant(Colls.fromArray(Base16.decode(contractSignature.daoKey).get))
    )
    params.put(
      "daoActionTokenIdAndStakeStateTokenId",
      ByteArrayConstant(
        Colls.fromArray(
          Paideia
            .getConfig(contractSignature.daoKey)
            .getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid) ++ Paideia
            .getConfig(contractSignature.daoKey)
            .getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid)
        )
      )
    )
    params.put(
      "paideiaDaoKey",
      ByteArrayConstant(Colls.fromArray(ErgoId.create(Env.paideiaDaoKey).getBytes))
    )
    params.put(
      "paideiaTokenId",
      ByteArrayConstant(Colls.fromArray(ErgoId.create(Env.paideiaTokenId).getBytes))
    )
    params.toMap
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        val utxos      = getUtxoSet.toList
        val candidates = utxos.map(boxes(_))
        val selection  = selectConsolidationSubset(cte.ctx, candidates)
        if (selection.length >= 5) {
          PaideiaEventResponse(
            1,
            List(ConsolidateTransaction(cte.ctx, selection))
          )
        } else {
          PaideiaEventResponse(0)
        }
      }
      case _: PaideiaEvent => PaideiaEventResponse(0)
    }
    PaideiaEventResponse.merge(List(super.handleEvent(event), response))
  }

  /** Selects a subset of the given candidate treasury boxes that can be safely
    * consolidated into a single output box without exceeding the node's box size limit.
    *
    * The candidates are sorted deterministically by number of tokens ascending (tiebreak
    * by box id), then greedily accumulated. After each box is tentatively added, a
    * candidate merged output box is built and checked against the node's box size limit,
    * the maximum number of distinct tokens per box, and the minimum box value. As soon as
    * a tentative addition would violate one of those checks, accumulation stops - since
    * boxes are visited in ascending token-count order, any box visited later would only
    * make things worse.
    *
    * @param ctx
    *   \- The context of the blockchain, used to build candidate output boxes.
    * @param candidates
    *   \- The treasury input boxes eligible for consolidation.
    * @return
    *   The (possibly empty) prefix of the sorted candidates that can be safely
    *   consolidated. Callers should check the length of the result (the on-chain
    *   contract requires at least 5 inputs) and whether the accumulated value covers the
    *   consolidation fee before firing a ConsolidateTransaction.
    */
  private def selectConsolidationSubset(
    ctx: BlockchainContextImpl,
    candidates: List[InputBox]
  ): List[InputBox] = {
    val sortedCandidates: List[InputBox] =
      candidates.sortBy((box: InputBox) =>
        (box.getTokens().size(), box.getId().toString())
      )

    var mergedTokens: HashMap[String, Long] = new HashMap[String, Long]()
    var totalValue: Long                    = 0L
    var selection: List[InputBox]           = List[InputBox]()
    var stillFits: Boolean                  = true

    sortedCandidates.foreach { (candidateBox: InputBox) =>
      if (stillFits) {
        val tentativeTokens: HashMap[String, Long] = mergedTokens.clone()
        candidateBox
          .getTokens()
          .forEach((token: ErgoToken) =>
            tentativeTokens.put(
              token.getId.toString(),
              token.getValue + tentativeTokens.getOrElse(token.getId.toString(), 0L)
            )
          )
        val tentativeValue: Long = totalValue + candidateBox.getValue()

        val fits: Boolean =
          if (tentativeTokens.size > 255) {
            false
          } else if (tentativeValue - 2000000L <= 0) {
            // Not enough accumulated value yet to build a valid output box - accept
            // the box tentatively, value only grows as more boxes are added.
            true
          } else {
            val firstBox: InputBox =
              if (selection.isEmpty) candidateBox else selection.head
            val tokensList: List[ErgoToken] =
              tentativeTokens
                .map { case (tokenId: String, amount: Long) =>
                  new ErgoToken(tokenId, amount)
                }
                .toList
            val outBoxBuilder = ctx
              .newTxBuilder()
              .outBoxBuilder()
              .contract(
                Address
                  .fromErgoTree(firstBox.getErgoTree(), ctx.getNetworkType())
                  .toErgoContract()
              )
              .value(tentativeValue - 2000000L)
            val candidateOutBox =
              if (tokensList.nonEmpty) outBoxBuilder.tokens(tokensList: _*).build()
              else outBoxBuilder.build()

            candidateOutBox.getBytesWithNoRef().size + 34 <= 4000 &&
            tentativeValue - 2000000L >= (candidateOutBox
              .getBytesWithNoRef()
              .size + 33) * 360L
          }

        if (fits) {
          selection = selection :+ candidateBox
          mergedTokens = tentativeTokens
          totalValue = tentativeValue
        } else {
          stillFits = false
        }
      }
    }

    if (selection.length >= 5 && totalValue - 2000000L > 0) selection
    else List[InputBox]()
  }

  /** It searches through all the boxes in the blockchain and matches the conditions to
    * find required number of boxes to fetch nanoERG and the provided array of ErgoTokens.
    *
    * @return
    *   Option[Array[InputBox]] if the required boxes found or else None.
    * @param nanoErgNeeded
    *   \- Amount of nanoERG needed.
    * @param tokensNeeded
    *   \- Array of ErgoTokens needed in the result.
    */
  def findBoxes(
    nanoErgNeeded: Long,
    tokensNeeded: Array[ErgoToken]
  ): Option[Array[InputBox]] = {
    var assetsFound  = false
    var nanoErgFound = 0L
    var tokensFound  = new java.util.HashMap[String, Long]()
    var result       = List[InputBox]()
    getUtxoSet
      .map(b => (b, boxes(b)))
      .foreach((box: (String, InputBox)) => {
        if (!assetsFound || result.length < 20) {
          result = result.::(box._2)
          nanoErgFound += box._2.getValue()
          box._2
            .getTokens()
            .forEach((token: ErgoToken) =>
              tokensFound.put(
                token.getId.toString(),
                token.getValue + tokensFound.getOrDefault(token.getId.toString(), 0L)
              )
            )
          assetsFound =
            nanoErgFound >= nanoErgNeeded && tokensNeeded.forall((token: ErgoToken) =>
              token.getValue <= tokensFound.getOrDefault(token.getId.toString(), 0L)
            )
        } else Unit
      })
    if (result.length > 0 && assetsFound) {
      Some(result.toArray)
    } else {
      if (nanoErgFound < nanoErgNeeded)
        throw new TreasuryShortfallErgsException(
          contractSignature.daoKey,
          nanoErgNeeded,
          nanoErgFound
        )
      else if (result.length > 0)
        throw new TreasuryShortfallTokensException(
          contractSignature.daoKey,
          tokensNeeded.map((t: ErgoToken) => (t.getId.toString(), t.getValue)).toMap,
          tokensFound.asScala.map((t: (String, Long)) => (t._1, t._2)).toMap
        )
      None
    }
  }
}

/** Companion Treasury object which extends Paideia Actor.
  */
object Treasury extends TypedPaideiaActor[Treasury](new Treasury(_))
