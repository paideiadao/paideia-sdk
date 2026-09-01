package im.paideia.app

import im.paideia.DAOConfigKey
import im.paideia.DAOConfigValueDeserializer
import im.paideia.Paideia
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.filtering.CompareField
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.governance.boxes.ActionSendFundsBasicBox
import im.paideia.governance.boxes.ActionUpdateConfigBox
import im.paideia.governance.boxes.ProposalBasicBox
import im.paideia.governance.contracts.ActionSendFundsBasic
import im.paideia.governance.contracts.ActionUpdateConfig
import im.paideia.util.ConfKeys
import im.paideia.util.Env
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoId
import scorex.crypto.hash.Blake2b256
import sigma.Coll

/** A DAO known to the current session, as far as the config box lookup can tell.
  *
  * @param key
  *   the DAO's key (its config NFT id).
  * @param name
  *   the DAO's display name (`ConfKeys.im_paideia_dao_name`).
  * @param configBoxCreationHeight
  *   the confirmed creation height of the DAO's current config box - the closest
  *   equivalent of "how long has this DAO existed" available without a dedicated genesis
  *   height field.
  */
case class DaoSummary(key: String, name: String, configBoxCreationHeight: Int)

/** A proposal's on-chain-visible summary: everything `ProposalBasicBox` carries, without
  * needing paideia-api's discussion/rationale layer.
  *
  * @param index
  *   the proposal's index within its DAO.
  * @param name
  *   the proposal's name (register R7, UTF-8).
  * @param endTime
  *   the proposal's voting deadline, as a unix-ms timestamp.
  * @param totalVotes
  *   total votes cast across every option.
  * @param voteCounts
  *   per-option vote tallies, in option order.
  * @param passed
  *   `None` while unresolved (`ProposalBasicBox.passed == -1`);
  *   `Some(true)`/`Some(false)` once evaluated.
  * @param boxId
  *   the proposal's current box id.
  */
case class ProposalSummary(
  index: Int,
  name: String,
  endTime: Long,
  totalVotes: Long,
  voteCounts: List[Long],
  passed: Option[Boolean],
  boxId: String
)

/** One output box a `SendFundsActionView` would create, decoded to human-readable form.
  *
  * @param address
  *   the recipient address, decoded from the output's proposition bytes via
  *   `Address.fromPropositionBytes` (network-aware - see [[ReadModels]]'s scaladoc for
  *   why this, unlike `PaideiaStateService.getDAOProposal`, doesn't hardcode
  *   `NetworkType.MAINNET`).
  * @param nanoErg
  *   the output's nanoERG value.
  * @param tokens
  *   the output's tokens, as (hex token id, amount) pairs.
  */
case class OutputView(address: String, nanoErg: Long, tokens: List[(String, Long)])

/** One action a proposal option can trigger, decoded from its on-chain action box. */
sealed trait ActionView

/** An `ActionSendFundsBasic` action: send funds to a fixed set of outputs, verbatim from
  * the chain - no rationale, just the literal effect.
  *
  * @param optionId
  *   the proposal option that activates this action.
  * @param activationTime
  *   when this action becomes executable, as a unix-ms timestamp.
  * @param outputs
  *   the exact output boxes this action creates.
  */
case class SendFundsActionView(
  optionId: Long,
  activationTime: Long,
  outputs: List[OutputView]
) extends ActionView

/** One DAO config tree mutation an `UpdateConfigActionView` will apply, decoded via
  * `DAOConfigValueDeserializer`.
  *
  * @param key
  *   the config key's resolved name (`DAOConfigKey.knownKeys`), or `"Unknown Key"` if
  *   this session has never seen the key constructed by name.
  * @param valueType
  *   the value's decoded type, as `DAOConfigValueDeserializer.getType` renders it (e.g.
  *   `"Long"`, `"Coll[Byte]"`).
  * @param value
  *   the value's decoded human-readable form, as `DAOConfigValueDeserializer.toString`
  *   renders it.
  */
case class ConfigEntryView(key: String, valueType: String, value: String)

/** An `ActionUpdateConfig` action: mutate the DAO config tree, verbatim from the chain.
  *
  * @param optionId
  *   the proposal option that activates this action.
  * @param activationTime
  *   when this action becomes executable, as a unix-ms timestamp.
  * @param remove
  *   config keys (resolved names) this action removes.
  * @param update
  *   config entries this action overwrites (the key must already exist).
  * @param insert
  *   config entries this action adds (the key must not already exist).
  */
case class UpdateConfigActionView(
  optionId: Long,
  activationTime: Long,
  remove: List[String],
  update: List[ConfigEntryView],
  insert: List[ConfigEntryView]
) extends ActionView

/** Full detail for one proposal: its summary, every decoded action any of its options
  * would trigger, and the raw per-voter vote records.
  *
  * @param summary
  *   the proposal's summary (see [[ProposalSummary]]).
  * @param actions
  *   every action box tied to this proposal, decoded to an [[ActionView]].
  * @param votes
  *   one entry per voter who has cast a vote: (stake key hex, per-option vote weights).
  */
case class ProposalDetail(
  summary: ProposalSummary,
  actions: List[ActionView],
  votes: List[(String, List[Long])]
)

/** Read-only queries over the current session's replayed state, framework-free port of
  * `services.PaideiaStateService`'s `getAllDAOs`/`getDAOProposals`/`getDAOProposal`
  * (paideia-state's `app/services/PaideiaStateService.scala`).
  *
  * Every method here reads `Paideia.current` (the session bound by the caller - see
  * [[StateLifecycle]]'s scaladoc for the binding pattern) directly; unlike the original,
  * there is no read/write lock (a CLI process is single-threaded - there is no concurrent
  * sync writer to guard against) and no `failIfSyncing` check (the CLI always calls
  * `StateLifecycle.bringUpToDate` to completion before running a query).
  *
  * Deviation from the original: `PaideiaStateService.getDAOProposal` hardcodes
  * `Address.fromPropositionBytes(NetworkType.MAINNET, ...)` when decoding a send-funds
  * action's output addresses; here it's `Address.fromPropositionBytes(Env.networkType,
  * ...)` - the network of whatever protocol instance this session is actually configured
  * for (mainnet or testnet), not a hardcoded mainnet assumption.
  */
object ReadModels {

  /** Every DAO known to the current session, skipping any whose current config box can't
    * be found (mirrors `PaideiaStateService.getAllDAOs`'s try/Some/None filter - a DAO
    * whose config contract instance has no confirmed boxes yet, or whose signature can't
    * be resolved, is simply omitted rather than failing the whole query).
    */
  def daoList(): List[DaoSummary] =
    Paideia._daoMap.toList.flatMap { case (daoKey, dao) =>
      try {
        val configContract = Config(
          dao
            .config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_config)
            .withDaoKey(dao.key)
        )
        val configBox = configContract.boxes(configContract.getUtxoSet.toList(0))
        Some(
          DaoSummary(
            daoKey,
            dao.config[String](ConfKeys.im_paideia_dao_name),
            configBox.getCreationHeight()
          )
        )
      } catch {
        case _: Throwable => None
      }
    }

  /** Decodes a single confirmed proposal box (matched by its `Coll[Int]` register 0's
    * first element, the proposal index - see `ProposalBasicBox.fromInputBox`) into a
    * [[ProposalSummary]].
    */
  private def proposalSummaryOf(
    ctx: BlockchainContextImpl,
    box: InputBox
  ): ProposalSummary = {
    val pbBox = ProposalBasicBox.fromInputBox(ctx, box)
    ProposalSummary(
      pbBox.proposalIndex,
      pbBox.name,
      pbBox.endTime,
      pbBox.totalVotes,
      pbBox.voteCount.toList,
      if (pbBox.passed == -1) None else Some(pbBox.passed == 1),
      box.getId().toString()
    )
  }

  /** Every running/resolved proposal for `daoKey`, oldest first - mirrors
    * `PaideiaStateService.getDAOProposals`, minus its `(index, name, height, boxId)`
    * tuple shape (replaced by the fuller [[ProposalSummary]], since the
    * tallies/endTime/passed flag `ProposalBasicBox` already carries are exactly what a
    * CLI user needs and cost nothing extra to decode here) and its "creationHeight > 0"
    * filter (replaced by the equivalent "a matching box was actually found" filter below,
    * which is what that height check was really testing for).
    */
  def proposalList(ctx: BlockchainContextImpl, daoKey: String): List[ProposalSummary] = {
    val proposalTokenId =
      new ErgoId(
        Paideia.getConfig(daoKey).getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid)
      )
        .toString()
    val proposalBoxes =
      Paideia.getBox(
        new FilterLeaf(FilterType.FTEQ, proposalTokenId, CompareField.ASSET, 0)
      )

    Paideia
      .getDAO(daoKey)
      .proposals
      .values
      .toList
      .flatMap { p =>
        proposalBoxes
          .find(box =>
            box
              .getRegisters()
              .get(0)
              .getValue()
              .asInstanceOf[Coll[Int]](0) == p.proposalIndex
          )
          .map(box => proposalSummaryOf(ctx, box))
      }
      .sortBy(_.index)
  }

  /** The config key's resolved name via `DAOConfigKey.knownKeys`, or `"Unknown Key"` if
    * this session never saw it constructed by name - mirrors
    * `PaideiaStateService.getDAOProposal`'s `properKnownKeys` lookup.
    */
  private def keyName(key: DAOConfigKey): String =
    DAOConfigKey.knownKeys.get(key.hashedKey.toList).flatten.getOrElse("Unknown Key")

  private def outputViewOf(box: sigma.Box): OutputView =
    OutputView(
      Address
        .fromPropositionBytes(Env.networkType, box.propositionBytes.toArray)
        .toString,
      box.value,
      box.tokens.map(t => (new ErgoId(t._1.toArray).toString(), t._2)).toArray.toList
    )

  /** Decodes a single confirmed action box into an [[ActionView]], dispatching on which
    * actor's contract instance the box's ErgoTree resolves to - mirrors
    * `PaideiaStateService.getDAOProposal`'s `actionContract match { ... }`.
    */
  private def actionViewOf(ctx: BlockchainContextImpl, box: InputBox): ActionView = {
    val actionContract = Paideia._actorList.values
      .flatMap(_.contractInstances)
      .toMap
      .get(Blake2b256(box.getErgoTree().bytes).array.toList)
      .getOrElse(
        throw new IllegalStateException(
          "ReadModels: no contract instance registered for action box " + box
            .getId()
            .toString()
        )
      )
    actionContract match {
      case _: ActionSendFundsBasic =>
        val ab = ActionSendFundsBasicBox.fromInputBox(ctx, box)
        SendFundsActionView(
          ab.optionId.toLong,
          ab.activationTime,
          ab.outputs.map(outputViewOf).toList
        )
      case _: ActionUpdateConfig =>
        val ab = ActionUpdateConfigBox.fromInputBox(ctx, box)
        UpdateConfigActionView(
          ab.optionId.toLong,
          ab.activationTime,
          ab.remove.map(keyName),
          ab.update.map { case (k, v) =>
            ConfigEntryView(
              keyName(k),
              DAOConfigValueDeserializer.getType(v),
              DAOConfigValueDeserializer.toString(v)
            )
          },
          ab.insert.map { case (k, v) =>
            ConfigEntryView(
              keyName(k),
              DAOConfigValueDeserializer.getType(v),
              DAOConfigValueDeserializer.toString(v)
            )
          }
        )
      case other =>
        throw new IllegalStateException(
          "ReadModels: unknown action contract " + other.getClass.getName
        )
    }
  }

  /** Full detail for `daoKey`'s proposal `index`: its summary, every decoded action box
    * tied to it, and its raw per-voter vote records - mirrors
    * `PaideiaStateService.getDAOProposal`.
    *
    * @throws NoSuchElementException
    *   if `daoKey` has no confirmed proposal box at `index`.
    */
  def proposalDetail(
    ctx: BlockchainContextImpl,
    daoKey: String,
    index: Int
  ): ProposalDetail = {
    val proposalTokenId =
      new ErgoId(
        Paideia.getConfig(daoKey).getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid)
      )
        .toString()
    val proposalBox = Paideia
      .getBox(new FilterLeaf(FilterType.FTEQ, proposalTokenId, CompareField.ASSET, 0))
      .find(box =>
        box.getRegisters().get(0).getValue().asInstanceOf[Coll[Int]](0) == index
      )
      .getOrElse(
        throw new NoSuchElementException(
          s"ReadModels.proposalDetail: no proposal $index for DAO $daoKey"
        )
      )

    val actionTokenId =
      new ErgoId(
        Paideia.getConfig(daoKey).getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid)
      )
        .toString()
    val actions = Paideia
      .getBox(new FilterLeaf(FilterType.FTEQ, actionTokenId, CompareField.ASSET, 0))
      .filter(box =>
        box.getRegisters().get(0).getValue().asInstanceOf[Coll[Long]](0) == index.toLong
      )
      .map(box => actionViewOf(ctx, box))

    val summary  = proposalSummaryOf(ctx, proposalBox)
    val pbBox    = ProposalBasicBox.fromInputBox(ctx, proposalBox)
    val proposal = Paideia.getDAO(daoKey).proposals(index)
    val votes = proposal.votes
      .getMap(pbBox.digestOpt)
      .get
      .toMap
      .map { case (stakeKey, record) => (stakeKey.toString(), record.votes.toList) }
      .toList

    ProposalDetail(summary, actions, votes)
  }
}
