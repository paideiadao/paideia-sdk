package im.paideia.staking

import com.google.common.primitives.Longs
import io.getblok.getblok_plasma.ByteConversion

case class StakeRecord(
  var stake: Long,
  var lockedUntil: Long,
  var rewards: List[Long]
) {
  def toBytes: Array[Byte] = {
    val rewardsBytes = rewards.map((l: Long) => Longs.toByteArray(l))
    Longs.toByteArray(lockedUntil) ++ Longs.toByteArray(
      stake
    ) ++ (for (list <- rewardsBytes; x <- list) yield x)
  }

  def clear = {
    stake       = 0L
    lockedUntil = 0L
    rewards     = rewards.map((l: Long) => 0L)
  }
}

object StakeRecord {
  implicit val stakeRecordConversion: ByteConversion[StakeRecord] =
    new ByteConversion[StakeRecord] {
      override def convertToBytes(t: StakeRecord): Array[Byte] = t.toBytes

      override def convertFromBytes(bytes: Array[Byte]): StakeRecord = {
        val stake       = Longs.fromByteArray(bytes.slice(8, 16))
        val lockedUntil = Longs.fromByteArray(bytes.slice(0, 8))
        val rewards =
          bytes.slice(16, bytes.size).grouped(8).map(Longs.fromByteArray(_)).toList
        StakeRecord(stake, lockedUntil, rewards)
      }
    }
}
