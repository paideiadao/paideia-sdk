package im.paideia.cli

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders a QR code as text, so the default ErgoPay tx-signing flow
  * (`Main.signAndSubmit`) can print one straight to a terminal - no image viewer, no
  * `--no-sign` JSON needed just to see the request. Uses zxing (the cli module's only
  * non-transitive library dependency - see `build.sbt`'s comment on why it's scoped to
  * this module alone) purely for QR encoding; the actual text rendering below is this
  * module's own.
  */
object Qr {

  /** Encodes `payload` (here, always an `ergopay:...` URI) as a QR code and renders it
    * with Unicode half-block characters (`▀`/`▄`/`█`/` `), two matrix rows per output
    * text line, so a normal monospace terminal font reproduces roughly square modules and
    * the code scans correctly from a phone camera. Error correction level `L` (payloads
    * here are a few hundred bytes - room for more ECC would just make the code bigger),
    * margin `2` modules.
    */
  def render(payload: String): String = {
    val hints = new java.util.HashMap[EncodeHintType, Any]()
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
    hints.put(EncodeHintType.MARGIN, Integer.valueOf(2))
    val matrix: BitMatrix =
      new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
    renderMatrix(matrix)
  }

  private def renderMatrix(matrix: BitMatrix): String = {
    val width  = matrix.getWidth
    val height = matrix.getHeight
    val sb     = new StringBuilder

    var y = 0
    while (y < height) {
      var x = 0
      while (x < width) {
        val top    = matrix.get(x, y)
        val bottom = if (y + 1 < height) matrix.get(x, y + 1) else false
        sb.append((top, bottom) match {
          case (true, true)   => '█' // full block: both rows dark
          case (true, false)  => '▀' // upper half block: top row dark only
          case (false, true)  => '▄' // lower half block: bottom row dark only
          case (false, false) => ' '
        })
        x += 1
      }
      sb.append('\n')
      y += 2
    }
    sb.toString()
  }
}
