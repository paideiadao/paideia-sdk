package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.Mint
import org.ergoplatform.appkit.InputBox
import java.nio.charset.StandardCharsets
import sigma.Coll
import im.paideia.Paideia

final case class MintBox(
  _ctx: BlockchainContextImpl,
  tokenId: String,
  mintAmount: Long,
  tokenName: String,
  tokenDescription: String,
  tokenDecimals: Int,
  useContract: Mint,
  _value: Long = 1000000L
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
  override def outBox: OutBox = {
    var b = ctx
      .newTxBuilder()
      .outBoxBuilder()
      .mintToken(
        new Eip4Token(
          tokenId,
          mintAmount,
          tokenName,
          tokenDescription,
          tokenDecimals
        )
      )
      .value(value)
      .contract(contract)
    b.build()
  }
}

object MintBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): MintBox = {
    val contract    = Mint.getContractInstanceFromTree(inp.getErgoTree())
    val mintedToken = inp.getTokens().get(0)
    val tokenName = new String(
      inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Byte]].toArray,
      StandardCharsets.UTF_8
    )
    val tokenDesc = new String(
      inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Byte]].toArray,
      StandardCharsets.UTF_8
    )
    val decimals = (new String(
      inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Byte]].toArray,
      StandardCharsets.UTF_8
    )).toInt
    MintBox(
      ctx,
      mintedToken.id.toString(),
      mintedToken.value,
      tokenName,
      tokenDesc,
      decimals,
      contract,
      inp.getValue()
    )
  }
}
