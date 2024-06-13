def stakeRecordProfits(stakeRecord: Coll[Byte]): Coll[Long] = {
    val profitOffset: Int = 16
    val longIndices: Coll[Int] = stakeRecord.slice(0, (stakeRecord.size-16)/8).indices.map{(i: Int) => i*8}
    longIndices.map{
        (i: Int) => 
        byteArrayToLong(
            stakeRecord.slice(i+profitOffset,i+8+profitOffset)
        )
    }
}