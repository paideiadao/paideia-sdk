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
import im.paideia.staking.ParticipationRecord
import im.paideia.staking.StakeRecord
import im.paideia.staking.TotalStakingState
import im.paideia.staking.boxes.StakeStateBox
import im.paideia.util.ConfKeys
import im.paideia.util.Env
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoId
import scorex.crypto.hash.Blake2b256
import sigma.Coll

import scala.collection.JavaConverters._

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
  *   the raw on-chain resolution flag, forwarded untouched (same as
  *   `PaideiaStateService`): `-1` while the proposal is still running, `-2` evaluated but
  *   the winning option didn't meet the threshold/quorum, otherwise the index of the
  *   winning option (see `EvaluateProposalBasicTransaction`).
  * @param boxId
  *   the proposal's current box id.
  */
case class ProposalSummary(
  index: Int,
  name: String,
  endTime: Long,
  totalVotes: Long,
  voteCounts: List[Long],
  passed: Int,
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

/** A single on-chain stake record, decoded from the DAO's current staking state - port of
  * `PaideiaStateActor.StakeInfo`/`PaideiaStateService.getStake` (paideia-state's
  * `app/actors/PaideiaStateActor.scala`/`app/services/PaideiaStateService.scala`).
  *
  * @param stakeKey
  *   the stake key NFT's token id (hex), the same id the user's proxy transactions
  *   (`UserTransactions.addStake`/`unstake`/`vote`) must reference.
  * @param stake
  *   the current `StakeRecord` (staked amount, lock, pending per-cycle rewards).
  * @param participation
  *   the current `ParticipationRecord` (voting participation), when one exists - a staker
  *   who has never voted has no participation record yet.
  */
case class StakeInfo(
  stakeKey: String,
  stake: StakeRecord,
  participation: Option[ParticipationRecord]
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
      pbBox.passed,
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

  /** `daoKey`'s current stake records for every token id in `candidateTokenIds` that
    * actually is a live stake key - port of `PaideiaStateService.getStake`: resolves the
    * DAO's current `StakeStateBox` (by its `im_paideia_staking_state_tokenid` NFT, same
    * as that box's own singleton-token lookup elsewhere in this class), then looks up
    * each candidate against `TotalStakingState(daoKey).currentStakingState`'s stake/
    * participation maps at that box's digests.
    *
    * Unlike the original (which takes the caller's already-known stake keys directly),
    * the CLI doesn't otherwise know a user's stake key - see [[candidateStakeKeysFor]]
    * for how it derives `candidateTokenIds` from a wallet's own unspent boxes instead.
    *
    * @return
    *   one [[StakeInfo]] per candidate that resolves to a live stake record - not
    *   necessarily in `candidateTokenIds`' order. Empty (never throwing) exactly when
    *   `candidateTokenIds` itself is empty, or when the DAO's staking state was resolved
    *   fine but none of `candidateTokenIds` turned out to be an actual key in it - both
    *   are the ordinary "this wallet has no stake here" outcome.
    * @throws NoSuchElementException
    *   (M4(a)) if `candidateTokenIds` is non-empty but the DAO's current `StakeStateBox`
    *   can't be found, or the stake-records/participation-records map at that box's own
    *   digest is missing - mirrors `PaideiaStateService.getStake`'s unguarded `.get`s on
    *   exactly those two lookups. Either is a local replay/sync problem (this session's
    *   state is stale or corrupt), never a legitimate "no stake" answer - silently
    *   returning `Nil` here would be indistinguishable from "this wallet has no stake",
    *   which is a materially different (and worse) thing to tell a caller deciding
    *   whether it's safe to mint a brand new stake key (see `Main`'s `stake add`
    *   handling).
    */
  def stakeStatus(
    ctx: BlockchainContextImpl,
    daoKey: String,
    candidateTokenIds: Set[String]
  ): List[StakeInfo] =
    if (candidateTokenIds.isEmpty) Nil
    else {
      val stakeStateTokenId = new ErgoId(
        Paideia
          .getConfig(daoKey)
          .getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid)
      ).toString()
      val box = Paideia
        .getBox(new FilterLeaf(FilterType.FTEQ, stakeStateTokenId, CompareField.ASSET, 0))
        .headOption
        .getOrElse(
          throw new NoSuchElementException(
            s"ReadModels.stakeStatus: no confirmed staking state box for DAO $daoKey - " +
              "local state may be out of sync"
          )
        )
      val stakeStateBox = StakeStateBox.fromInputBox(ctx, box)
      val state         = TotalStakingState(daoKey).currentStakingState
      val stakeMap = state.stakeRecords
        .getMap(Some(stakeStateBox.stateDigest))
        .getOrElse(
          throw new NoSuchElementException(
            s"ReadModels.stakeStatus: no stake-records map at the current staking " +
              s"state's digest for DAO $daoKey - local state may be out of sync"
          )
        )
        .toMap
      val partMap = state.participationRecords
        .getMap(Some(stakeStateBox.participationDigest))
        .getOrElse(
          throw new NoSuchElementException(
            s"ReadModels.stakeStatus: no participation-records map at the current " +
              s"staking state's digest for DAO $daoKey - local state may be out of sync"
          )
        )
        .toMap

      candidateTokenIds.toList.flatMap { tokenId =>
        try {
          val key = ErgoId.create(tokenId)
          stakeMap
            .get(key)
            .map(record => StakeInfo(key.toString(), record, partMap.get(key)))
        } catch {
          case _: Throwable => None
        }
      }
    }

  /** Every token id sitting in any of `addresses`' unspent boxes - the CLI's stand-in for
    * "the stake keys this user might hold for any DAO", since (unlike paideia-api, which
    * tracks stake keys per user account) a node-only CLI has no other record of which
    * token in a user's wallet is a stake key versus an ordinary asset. Cheap to compute
    * (reuses [[UserBoxSelector]]'s own box fetch) and safe to overshoot: [[stakeStatus]]
    * itself filters this down to whichever candidates actually resolve to a live stake
    * record for the DAO being queried.
    */
  def candidateStakeKeysFor(
    selector: UserBoxSelector,
    addresses: Seq[String]
  ): Set[String] =
    selector
      .unspentBoxes(addresses)
      .flatMap(_.getTokens().asScala.map(_.getId.toString))
      .toSet
}
