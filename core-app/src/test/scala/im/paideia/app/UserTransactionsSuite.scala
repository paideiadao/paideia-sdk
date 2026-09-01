package im.paideia.app

import im.paideia.DAO
import im.paideia.DAOConfig
import im.paideia.Paideia
import im.paideia.common.PaideiaTestSuite
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.Proposal
import im.paideia.governance.contracts.ProposalBasic
import im.paideia.governance.transactions.CastVoteTransaction
import im.paideia.staking.StakeRecord
import im.paideia.staking.StakingTest
import im.paideia.staking.TotalStakingState
import im.paideia.staking.contracts.ChangeStake
import im.paideia.staking.contracts.Stake
import im.paideia.staking.contracts.StakeState
import im.paideia.staking.contracts.StakeVote
import im.paideia.staking.contracts.Unstake
import im.paideia.staking.transactions.AddStakeTransaction
import im.paideia.staking.transactions.StakeTransaction
import im.paideia.staking.transactions.UnstakeTransaction
import im.paideia.util.ConfKeys
import im.paideia.util.Util
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.sdk.ErgoToken

/** Offline coverage of [[UserTransactions]]'s four builders: each is exercised against
  * the same in-memory DAO/staking-state fixtures `StakeTransactionSuite`/
  * `AddStakeTransactionSuite`/`UnstakeTransactionSuite`/`CastVoteTransactionSuite` use
  * for the underlying direct SDK transactions, with a stub [[UserBoxSelector]] standing
  * in for a live wallet - no `IndexedNodeClient`, no network. Assertions focus on what
  * `UserTransactions` itself is responsible for: producing the right concrete transaction
  * type, with `userInputs` filled in well enough that `unsigned()` builds without error
  * (a `fundsMissing()`/`NotEnoughErgsException` failure here would mean the stub wallet
  * fixture is wrong, not that `UserTransactions` is - so a clean `unsigned()` is exactly
  * the signal this suite needs).
  */
class UserTransactionsSuite extends PaideiaTestSuite {

  private val walletAddressStr = "4MQyML64GnzMxZgm"
  private val walletAddress    = Address.create(walletAddressStr)

  private def wallet(boxes: InputBox*): UserBoxSelector =
    new UserBoxSelector(_ => boxes.toList)

  private def fundingBox(
    ctx: BlockchainContextImpl,
    value: Long,
    tokens: List[ErgoToken] = Nil
  ): InputBox = {
    var b = ctx
      .newTxBuilder()
      .outBoxBuilder()
      .contract(walletAddress.toErgoContract())
      .value(value)
    if (tokens.nonEmpty) b = b.tokens(tokens: _*)
    b.build().convertToInputWith(Util.randomKey, 0.toShort)
  }

  /** `stake`/`addStake` both increase `StakeStateBox.stakedTokenTotal` by the amount
    * being (added to the) stake, which the resulting output box must carry - just like
    * the stake-key NFT, that extra governance-token amount has to come from the user's
    * own wallet (see `StakeStateBox.stake`/`addStake`), not just ERG headroom.
    */
  private def daoTokenIdOf(dao: im.paideia.DAO): String =
    new org.ergoplatform.sdk.ErgoId(
      dao.config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid)
    )
      .toString()

  private def withCtx(body: BlockchainContextImpl => Unit): Unit = {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        body(ctx)
      }
    })
  }

  test("stake: builds a StakeTransaction whose unsigned() form builds") {
    withCtx { ctx =>
      val dao = StakingTest.testDAO

      val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
      dao.config.set(
        ConfKeys.im_paideia_contracts_staking_state,
        stakingContract.contractSignature
      )
      val stakingState = stakingContract.emptyBox(ctx, dao, 100000000L)

      val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
      dao.config.set(
        ConfKeys.im_paideia_contracts_config,
        configContract.contractSignature
      )
      configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

      // StakeTransaction spends the Stake contract's own single utility box as its
      // second input (alongside the StakeState box) - see StakeTransactionSuite.
      val stakeContract = Stake(PaideiaContractSignature(daoKey = dao.key))
      stakeContract.newBox(stakeContract.box(ctx, 1000000L).inputBox(), false)

      stakingContract.clearBoxes()
      stakingContract.newBox(stakingState.inputBox(), false)

      // The 1,000 raw units being staked must come from the wallet, alongside ERG
      // headroom - see daoTokenIdOf's scaladoc.
      val selector =
        wallet(fundingBox(ctx, 5000000L, List(new ErgoToken(daoTokenIdOf(dao), 1000L))))
      val tx = UserTransactions.stake(
        ctx,
        selector,
        dao.key,
        1000L,
        List(walletAddressStr),
        walletAddressStr
      )

      assert(tx.isInstanceOf[StakeTransaction])
      tx.unsigned() // must not throw
    }
  }

  test("addStake: builds an AddStakeTransaction whose unsigned() form builds") {
    withCtx { ctx =>
      val dao     = StakingTest.testDAO
      val testKey = Util.randomKey

      val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
      dao.config.set(
        ConfKeys.im_paideia_contracts_staking_state,
        stakingContract.contractSignature
      )

      val changeStakeContract = ChangeStake(PaideiaContractSignature(daoKey = dao.key))
      changeStakeContract.newBox(changeStakeContract.box(ctx).inputBox(), false)

      val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
      dao.config.set(
        ConfKeys.im_paideia_contracts_config,
        configContract.contractSignature
      )
      configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

      val stakingState = stakingContract.emptyBox(ctx, dao, 100000000L)
      stakingState.stake(testKey, 100L)
      stakingContract.clearBoxes()
      stakingContract.newBox(stakingState.inputBox(), false)

      // The wallet must hold the stake-key NFT itself (spent and returned by
      // AddStakeTransaction), the 3,000,000 raw units being added to the stake, and
      // enough ERG headroom.
      val selector = wallet(
        fundingBox(
          ctx,
          5000000L,
          List(new ErgoToken(testKey, 1L), new ErgoToken(daoTokenIdOf(dao), 3000000L))
        )
      )
      val tx = UserTransactions.addStake(
        ctx,
        selector,
        dao.key,
        testKey,
        3000000L,
        List(walletAddressStr),
        walletAddressStr
      )

      assert(tx.isInstanceOf[AddStakeTransaction])
      tx.unsigned()
    }
  }

  test("unstake: builds an UnstakeTransaction (partial) whose unsigned() form builds") {
    withCtx { ctx =>
      val dao     = StakingTest.testDAO
      val testKey = Util.randomKey

      val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
      dao.config.set(
        ConfKeys.im_paideia_contracts_staking_state,
        stakingContract.contractSignature
      )

      val stakingState = stakingContract.emptyBox(ctx, dao, 100000000L)
      stakingState.stake(testKey, 10000L)

      val changeStakeContract = ChangeStake(PaideiaContractSignature(daoKey = dao.key))
      changeStakeContract.newBox(changeStakeContract.box(ctx).inputBox(), false)

      val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
      dao.config.set(
        ConfKeys.im_paideia_contracts_config,
        configContract.contractSignature
      )
      configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

      stakingContract.clearBoxes()
      stakingContract.newBox(stakingState.inputBox(), false)

      val currentStake = stakingState.getStake(testKey)
      val newRecord =
        StakeRecord(
          currentStake.stake - 300L,
          currentStake.lockedUntil,
          currentStake.rewards
        )

      val selector = wallet(fundingBox(ctx, 5000000L, List(new ErgoToken(testKey, 1L))))
      val tx = UserTransactions.unstake(
        ctx,
        selector,
        dao.key,
        testKey,
        newRecord,
        List(walletAddressStr),
        walletAddressStr
      )

      assert(tx.isInstanceOf[UnstakeTransaction])
      tx.unsigned()
    }
  }

  test("unstake: a full unstake (stake = 0) also builds") {
    withCtx { ctx =>
      val dao     = StakingTest.testDAO
      val testKey = Util.randomKey

      val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
      dao.config.set(
        ConfKeys.im_paideia_contracts_staking_state,
        stakingContract.contractSignature
      )

      val stakingState = stakingContract.emptyBox(ctx, dao, 100000000L)
      stakingState.stake(testKey, 10000L)

      val unstakeContract = Unstake(PaideiaContractSignature(daoKey = dao.key))
      unstakeContract.newBox(unstakeContract.box(ctx).inputBox(), false)

      val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
      dao.config.set(
        ConfKeys.im_paideia_contracts_config,
        configContract.contractSignature
      )
      configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

      stakingContract.clearBoxes()
      stakingContract.newBox(stakingState.inputBox(), false)

      val currentStake = stakingState.getStake(testKey)
      currentStake.clear

      val selector = wallet(fundingBox(ctx, 5000000L, List(new ErgoToken(testKey, 1L))))
      val tx = UserTransactions.unstake(
        ctx,
        selector,
        dao.key,
        testKey,
        currentStake,
        List(walletAddressStr),
        walletAddressStr
      )

      assert(tx.isInstanceOf[UnstakeTransaction])
      tx.unsigned()
    }
  }

  test("vote: builds a CastVoteTransaction whose unsigned() form builds") {
    withCtx { ctx =>
      // CastVoteTransaction (unlike Stake/AddStake/Unstake) needs a DAO config carrying
      // im_paideia_dao_proposal_tokenid (to find the proposal box) - StakingTest.testDAO
      // doesn't set that key, so this mirrors CastVoteTransactionSuite's own manual DAO
      // setup instead.
      val daoKey            = Util.randomKey
      val config            = DAOConfig(daoKey)
      val daoGovTokenId     = Util.randomKey
      val proposalTokenId   = Util.randomKey
      val actionTokenId     = Util.randomKey
      val stakeStateTokenId = Util.randomKey
      val testKey           = Util.randomKey

      val dao = new DAO(daoKey, config)
      Paideia.addDAO(dao)

      config.set(ConfKeys.im_paideia_dao_name, "Test DAO")
      config.set(ConfKeys.im_paideia_dao_tokenid, ErgoId.create(daoGovTokenId).getBytes)
      config.set(
        ConfKeys.im_paideia_dao_proposal_tokenid,
        ErgoId.create(proposalTokenId).getBytes
      )
      config.set(
        ConfKeys.im_paideia_dao_action_tokenid,
        ErgoId.create(actionTokenId).getBytes
      )
      config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
      config.set(
        ConfKeys.im_paideia_staking_state_tokenid,
        ErgoId.create(stakeStateTokenId).getBytes
      )
      config.set(ConfKeys.im_paideia_staking_emission_delay, 4L)
      config.set(ConfKeys.im_paideia_staking_emission_amount, 100000L)
      config.set(ConfKeys.im_paideia_staking_cyclelength, 1000000L)

      val voteContract = StakeVote(PaideiaContractSignature(daoKey = daoKey))
      config.set(
        ConfKeys.im_paideia_contracts_staking_vote,
        voteContract.contractSignature
      )
      voteContract.newBox(voteContract.box(ctx).inputBox(), false)

      dao.proposals(0) = Proposal(dao.key, 0, "test")
      val proposalContract = ProposalBasic(PaideiaContractSignature(daoKey = dao.key))
      val proposalBox = proposalContract.box(
        ctx,
        "test",
        0,
        Array(0L, 0L),
        0L,
        ctx.createPreHeader().build().getTimestamp() + 3600000,
        -1
      )
      proposalContract.clearBoxes()
      proposalContract.newBox(proposalBox.inputBox(), false)

      TotalStakingState(dao.key, 0L)

      val stakingContract = StakeState(PaideiaContractSignature(daoKey = dao.key))
      config.set(
        ConfKeys.im_paideia_contracts_staking_state,
        stakingContract.contractSignature
      )

      val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
      config.set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
      configContract.newBox(configContract.box(ctx, dao).inputBox(), false)

      val stakingState = stakingContract.emptyBox(ctx, dao, 100000000L)
      stakingState.stake(testKey, 100L)
      stakingContract.clearBoxes()
      stakingContract.newBox(stakingState.inputBox(), false)

      val selector = wallet(fundingBox(ctx, 5000000L, List(new ErgoToken(testKey, 1L))))
      val tx = UserTransactions.vote(
        ctx,
        selector,
        dao.key,
        testKey,
        0,
        Array(100L, 0L),
        List(walletAddressStr),
        walletAddressStr
      )

      assert(tx.isInstanceOf[CastVoteTransaction])
      val unsigned     = tx.unsigned()
      val reduced      = ctx.newProverBuilder().build().reduce(unsigned, 0)
      val reducedBytes = reduced.toBytes()

      // Reported in the implementation notes as the realistic size of a single-voter
      // CastVoteTransaction's reduced form - i.e. what actually goes into the ErgoPay
      // `ergopay://.../tx` payload/QR code for a real `vote` command.
      info(
        s"vote tx: reduced form is ${reducedBytes.length} bytes " +
          s"(${Eip12UnsignedTx.toJson(Eip12UnsignedTx(unsigned)).length} bytes as EIP-12 JSON)"
      )
      assert(reducedBytes.length > 0)
    }
  }
}
