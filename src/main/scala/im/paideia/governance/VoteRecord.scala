package im.paideia.governance

import com.google.common.primitives.Longs
import io.getblok.getblok_plasma.ByteConversion

case class VoteRecord(votes: Array[Long])

object VoteRecord {
    implicit val convertsVoteRecord: ByteConversion[VoteRecord] = new ByteConversion[VoteRecord] {
        override def convertToBytes(t: VoteRecord): Array[Byte] = t.votes.flatMap(Longs.toByteArray(_))

        override def convertFromBytes(bytes: Array[Byte]): VoteRecord = VoteRecord(bytes.grouped(8).map(Longs.fromByteArray(_)).toArray)
    }
}
