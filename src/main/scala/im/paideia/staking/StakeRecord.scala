package im.paideia.staking

import com.google.common.primitives.Longs
import io.getblok.getblok_plasma.ByteConversion

case class StakeRecord (
    stake: Long,
    rewards: List[Long]
) {
    def toBytes: Array[Byte] = {
        val rewardsBytes = rewards.map((l: Long) => Longs.toByteArray(l))
        val res = Longs.toByteArray(stake) ++ (for (list <- rewardsBytes; x <- list) yield x)
        res
    }
}

object StakeRecord {
    implicit val stakeRecordConversion: ByteConversion[StakeRecord] = new ByteConversion[StakeRecord] {
        override def convertToBytes(t: StakeRecord): Array[Byte] = t.toBytes

        override def convertFromBytes(bytes: Array[Byte]): StakeRecord = {
            val stake = Longs.fromByteArray(bytes.slice(0,8))
            val rewards = bytes.slice(8,bytes.length).grouped(8).map(Longs.fromByteArray(_)).toList
            StakeRecord(stake,rewards) 
        }
    }
}