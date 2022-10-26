package im.paideia.governance.transactions

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import im.paideia.DAOConfig
import org.ergoplatform.ErgoAddress
import im.paideia.Paideia
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.util.Env
import im.paideia.common.filtering.CompareField
import im.paideia.governance.contracts.DAOOrigin
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.Config
import im.paideia.DAO
import org.ergoplatform.appkit.ContextVar
import im.paideia.DAOConfigKey
import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.OutBox
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import javax.naming.Context

final case class CreateDAOTransaction(
    _ctx: BlockchainContextImpl,
    protoDAOInput: InputBox,
    dao: DAO,
    _changeAddress: ErgoAddress
) extends PaideiaTransaction
{
    val paideiaConfigBox = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        Env.paideiaDaoKey,
        CompareField.ASSET,
        0
    ))(0)
    val voteMintBox = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_vote_tokenid)).toString(),
        CompareField.ASSET,
        0
    ))(0)
    val actionMintBox = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid)).toString(),
        CompareField.ASSET,
        0
    ))(0)
    val proposalMintBox = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid)).toString(),
        CompareField.ASSET,
        0
    ))(0)
    val daoKeyMintBox = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_key)).toString(),
        CompareField.ASSET,
        0
    ))(0)
    

    val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)
    
    val daoOriginOutput = DAOOrigin(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=Env.paideiaDaoKey)).box(
        _ctx,
        dao,
        Long.MaxValue,
        Long.MaxValue,
        Long.MaxValue
    )

    val configOutput = Config(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=dao.key)).box(_ctx,dao)
    val contextVarsProtoDAO = List(
        ContextVar.of(0.toByte,1.toByte),
        ContextVar.of(1.toByte,paideiaConfig.getProof(
            ConfKeys.im_paideia_contracts_dao,
            ConfKeys.im_paideia_contracts_config
        )),
        ContextVar.of(2.toByte,dao.config.getProof(
            ConfKeys.im_paideia_dao_proposal_tokenid,
            ConfKeys.im_paideia_dao_vote_tokenid,
            ConfKeys.im_paideia_dao_action_tokenid,
            ConfKeys.im_paideia_dao_key
        ))
    )
    val mintPaideiaConfigProof = ContextVar.of(0.toByte,paideiaConfig.getProof(
        ConfKeys.im_paideia_contracts_protodao,
        ConfKeys.im_paideia_contracts_dao))
    val proposalMintContext = List(
        mintPaideiaConfigProof,
        ContextVar.of(1.toByte,dao.config.getProof(ConfKeys.im_paideia_dao_proposal_tokenid)),
        ContextVar.of(2.toByte,ConfKeys.im_paideia_dao_proposal_tokenid.ergoValue)
    )
    val actionMintContext = List(
        mintPaideiaConfigProof,
        ContextVar.of(1.toByte,dao.config.getProof(ConfKeys.im_paideia_dao_action_tokenid)),
        ContextVar.of(2.toByte,ConfKeys.im_paideia_dao_action_tokenid.ergoValue)
    )
    val voteMintContext = List(
        mintPaideiaConfigProof,
        ContextVar.of(1.toByte,dao.config.getProof(ConfKeys.im_paideia_dao_vote_tokenid)),
        ContextVar.of(2.toByte,ConfKeys.im_paideia_dao_vote_tokenid.ergoValue)
    )
    val daoKeyMintContext = List(
        mintPaideiaConfigProof,
        ContextVar.of(1.toByte,dao.config.getProof(ConfKeys.im_paideia_dao_key)),
        ContextVar.of(2.toByte,ConfKeys.im_paideia_dao_key.ergoValue)
    )
    ctx = _ctx
    fee = 1000000
    changeAddress = _changeAddress
    inputs = List[InputBox](
        protoDAOInput.withContextVars(contextVarsProtoDAO:_*),
        proposalMintBox.withContextVars(proposalMintContext:_*),
        actionMintBox.withContextVars(actionMintContext:_*),
        voteMintBox.withContextVars(voteMintContext:_*),
        daoKeyMintBox.withContextVars(daoKeyMintContext:_*))
    dataInputs = List[InputBox](paideiaConfigBox)
    outputs = List[OutBox](daoOriginOutput.outBox,configOutput.outBox)
}