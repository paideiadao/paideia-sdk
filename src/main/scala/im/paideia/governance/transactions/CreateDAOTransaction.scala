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
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.staking.TotalStakingState
import im.paideia.governance.boxes.ProtoDAOBox

case class CreateDAOTransaction(
    _ctx: BlockchainContextImpl,
    protoDAOInput: InputBox,
    dao: DAO,
    _changeAddress: ErgoAddress
) extends PaideiaTransaction
{
    val protoDAOInputBox = ProtoDAOBox.fromInputBox(_ctx,protoDAOInput)
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
    val stakeStateMintBox = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid)).toString(),
        CompareField.ASSET,
        0
    ))(0)
    

    val paideiaConfig = Paideia.getConfig(Env.paideiaDaoKey)
    
    val daoOriginOutput = DAOOrigin(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=dao.key)).box(
        _ctx,
        dao,
        Long.MaxValue,
        Long.MaxValue,
        Long.MaxValue
    )

    val configOutput = Config(PaideiaContractSignature(networkType=_ctx.getNetworkType(),daoKey=dao.key)).box(_ctx,dao)

    val state = TotalStakingState(dao.key,_ctx.createPreHeader().build().getTimestamp()+dao.config[Long](ConfKeys.im_paideia_staking_cyclelength))
    val stakeStateOutput = PlasmaStaking(PaideiaContractSignature(daoKey=dao.key)).box(_ctx,dao.config,state,protoDAOInputBox.stakePool,1000000L)

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
            ConfKeys.im_paideia_dao_key,
            ConfKeys.im_paideia_staking_state_tokenid
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
    val stakeStateMintContext = List(
        mintPaideiaConfigProof,
        ContextVar.of(1.toByte,dao.config.getProof(ConfKeys.im_paideia_staking_state_tokenid)),
        ContextVar.of(2.toByte,ConfKeys.im_paideia_staking_state_tokenid.ergoValue)
    )
    ctx = _ctx
    fee = 1000000
    changeAddress = _changeAddress
    inputs = List[InputBox](
        protoDAOInput.withContextVars(contextVarsProtoDAO:_*),
        proposalMintBox.withContextVars(proposalMintContext:_*),
        actionMintBox.withContextVars(actionMintContext:_*),
        voteMintBox.withContextVars(voteMintContext:_*),
        daoKeyMintBox.withContextVars(daoKeyMintContext:_*),
        stakeStateMintBox.withContextVars(stakeStateMintContext:_*))
    dataInputs = List[InputBox](paideiaConfigBox)
    outputs = List[OutBox](daoOriginOutput.outBox,configOutput.outBox,stakeStateOutput.outBox)
}