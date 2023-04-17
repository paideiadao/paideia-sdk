package im.paideia.staking

import com.google.common.primitives.Longs
import io.getblok.getblok_plasma.ByteConversion

case class StakeRecord(
  var lockedUntil: Long,
  var voted: Long,
  var stake: Long,
  var rewards: List[Long]
) {
  def toBytes: Array[Byte] = {
    val rewardsBytes = rewards.map((l: Long) => Longs.toByteArray(l))
    val res =
      Longs.toByteArray(lockedUntil) ++ Longs.toByteArray(voted) ++ Longs.toByteArray(
        stake
      ) ++ (for (list <- rewardsBytes; x <- list) yield x)
    res
  }

  def clear = {
    lockedUntil = 0L
    voted       = 0L
    stake       = 0L
    rewards     = rewards.map((l: Long) => 0L)
  }
}

object StakeRecord {
  implicit val stakeRecordConversion: ByteConversion[StakeRecord] =
    new ByteConversion[StakeRecord] {
      override def convertToBytes(t: StakeRecord): Array[Byte] = t.toBytes

      override def convertFromBytes(bytes: Array[Byte]): StakeRecord = {
        val lockedUntil = Longs.fromByteArray(bytes.slice(0, 8))
        val voted       = Longs.fromByteArray(bytes.slice(8, 16))
        val stake       = Longs.fromByteArray(bytes.slice(16, 24))
        val rewards =
          bytes.slice(24, bytes.length).grouped(8).map(Longs.fromByteArray(_)).toList
        StakeRecord(lockedUntil, voted, stake, rewards)
      }
    }
}
