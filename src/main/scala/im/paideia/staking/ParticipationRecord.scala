package im.paideia.staking

import com.google.common.primitives.Longs
import work.lithos.plasma.ByteConversion

case class ParticipationRecord(
  var voted: Long,
  var votedTotal: Long
) {
  def toBytes: Array[Byte] = {
    Longs.toByteArray(voted) ++ Longs.toByteArray(
      votedTotal
    )
  }

  def clear = {
    voted      = 0L
    votedTotal = 0L
  }
}

object ParticipationRecord {
  implicit val participationRecordConversion: ByteConversion[ParticipationRecord] =
    new ByteConversion[ParticipationRecord] {
      override def convertToBytes(t: ParticipationRecord): Array[Byte] = t.toBytes

      override def convertFromBytes(bytes: Array[Byte]): ParticipationRecord = {
        val voted      = Longs.fromByteArray(bytes.slice(0, 8))
        val votedTotal = Longs.fromByteArray(bytes.slice(8, 16))
        ParticipationRecord(voted, votedTotal)
      }
    }
}
