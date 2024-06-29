package im.paideia.common.transactions

import im.paideia.common.PaideiaTestSuite
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.util.Util
import im.paideia.DAOConfig
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoId
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.events.CreateTransactionsEvent

class UpdateOrRefreshTransactionSuite extends PaideiaTestSuite {
  test("Refresh config") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]

        PaideiaTestSuite.init(ctx)
        val daoKey = Util.randomKey
        val config = DAOConfig(daoKey)

        val actionTokenId = Util.randomKey
        config.set(
          ConfKeys.im_paideia_dao_action_tokenid,
          ErgoId.create(actionTokenId).getBytes
        )
        config.set(ConfKeys.im_paideia_dao_key, ErgoId.create(daoKey).getBytes)
        val dao = new DAO(daoKey, config)

        Paideia.addDAO(dao)

        val configContract = Config(PaideiaContractSignature(daoKey = dao.key))
        config.set(ConfKeys.im_paideia_contracts_config, configContract.contractSignature)
        configContract.clearBoxes()
        configContract.newBox(
          configContract.box(ctx, dao).withCreationHeight(0).inputBox(),
          false
        )

        val eventResponse = Paideia.handleEvent(
          CreateTransactionsEvent(ctx, 1000L, 600000L)
        )
        eventResponse.exceptions.map(e => throw e)
        val unsigneds = eventResponse.unsignedTransactions
          .filter(pt =>
            pt match {
              case UpdateOrRefreshTransaction(
                    _ctx,
                    outdatedBoxes,
                    longLivingKey,
                    _dao,
                    newAddress,
                    operatorAddress
                  ) =>
                dao == _dao && longLivingKey == ConfKeys.im_paideia_contracts_config.originalKey.get
              case _: PaideiaTransaction => false
            }
          )
        assert(
          unsigneds.size === 1
        )
        ctx
          .newProverBuilder()
          .build()
          .sign(unsigneds(0).unsigned)
      }
    })
  }
}
