package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoToken
import im.paideia.util.Env
import im.paideia.governance.contracts.PaideiaOrigin
import org.ergoplatform.appkit.InputBox

final case class PaideiaOriginBox(
  _ctx: BlockchainContextImpl,
  _value: Long,
  daoTokensRemaining: Long,
  useContract: PaideiaOrigin
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
  tokens = List(
    new ErgoToken(Env.paideiaOriginNFT, 1L),
    new ErgoToken(Env.daoTokenId, daoTokensRemaining)
  )
}

object PaideiaOriginBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): PaideiaOriginBox = {
    val contract =
      PaideiaOrigin.getContractInstanceFromTree[PaideiaOrigin](inp.getErgoTree())
    val nft       = inp.getTokens().get(0)
    val daoTokens = inp.getTokens().get(1)
    if (!nft.id.toString().equals(Env.paideiaOriginNFT))
      throw new Exception("PaideiaOrigin NFT has wrong ID")
    if (!daoTokens.id.toString().equals(Env.daoTokenId))
      throw new Exception("DAO Token has wrong ID")
    PaideiaOriginBox(ctx, inp.getValue(), daoTokens.value, contract)
  }
}
