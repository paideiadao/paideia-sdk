def stakeRecordStake(stakeRecord: Coll[Byte]): Long = {
    byteArrayToLong(stakeRecord.slice(8,16))
}