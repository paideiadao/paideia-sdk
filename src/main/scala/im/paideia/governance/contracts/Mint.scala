package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaActor
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.boxes.MintBox
import java.util.HashMap
import im.paideia.util.ConfKeys
import im.paideia.util.Env
import org.ergoplatform.sdk.ErgoId

class Mint(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {
  def box(
    ctx: BlockchainContextImpl,
    tokenId: String,
    mintAmount: Long,
    tokenName: String,
    tokenDescription: String,
    tokenDecimals: Int
  ): MintBox = {
    val res = new MintBox(tokenId, mintAmount, tokenName, tokenDescription, tokenDecimals)
    res.ctx      = ctx
    res.value    = 1000000L
    res.contract = contract
    res
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_PROTODAO",
      ConfKeys.im_paideia_contracts_protodao.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_DAO",
      ConfKeys.im_paideia_contracts_dao.ergoValue.getValue()
    )
    cons.put("_PAIDEIA_DAO_KEY", ErgoId.create(Env.paideiaDaoKey).getBytes)
    cons
  }
}

object Mint extends PaideiaActor {
  override def apply(contractSignature: PaideiaContractSignature): Mint =
    getContractInstance[Mint](contractSignature, new Mint(contractSignature))
}
