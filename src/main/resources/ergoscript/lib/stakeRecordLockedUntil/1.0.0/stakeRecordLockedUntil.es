def stakeRecordLockedUntil(stakeRecord: Coll[Byte]): Long = {
    byteArrayToLong(stakeRecord.slice(0,8))
}