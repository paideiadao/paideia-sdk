package im.paideia.governance

import com.google.common.primitives.Longs
import io.getblok.getblok_plasma.ByteConversion

case class VoteRecord(votes: Array[Long])
{
    def voteCount: Long = votes.fold(0L)(_ + _)

    def +(that: VoteRecord): VoteRecord = VoteRecord(votes.zip(that.votes).map((ll: (Long,Long)) => ll._1 + ll._2))
    def -(that: VoteRecord): VoteRecord = VoteRecord(votes.zip(that.votes).map((ll: (Long,Long)) => ll._1 - ll._2))
}

object VoteRecord {
    implicit val convertsVoteRecord: ByteConversion[VoteRecord] = new ByteConversion[VoteRecord] {
        override def convertToBytes(t: VoteRecord): Array[Byte] = t.votes.flatMap(Longs.toByteArray(_))

        override def convertFromBytes(bytes: Array[Byte]): VoteRecord = VoteRecord(bytes.grouped(8).map(Longs.fromByteArray(_)).toArray)
    }
}
