#import lib/vlqByteSize/1.0.0/vlqByteSize.es;

//Normal box will have following bytes structure when serialized:
// value(ulong)
// ergotreebytes(X)
// creationheight(uint)
// tokensize(ubyte)
//  - tokenId(32)
//  - tokenamount(ulong)
// numberofadditionalregs(ubyte)
//  - registervalue(SValue)
// txId(32)
// index(ushort)
//
// bytesWithoutRef is this without the txId and index

def updateOrRefresh(contractHash: Coll[Byte]): Boolean = {
    val inputIndex = INPUTS.indexOf(SELF,0)
    val selfOutput = OUTPUTS(inputIndex)
    val creationDifference = selfOutput.creationInfo._1 - SELF.creationInfo._1

    val inputValueSize = vlqByteSize(SELF.value)
    val outputValueSize = vlqByteSize(selfOutput.value)

    val inputCreationHeightSize = vlqByteSize(SELF.creationInfo._1.toLong)
    val outputCreationHeightSize = vlqByteSize(selfOutput.creationInfo._1.toLong)

    val inputBytes = SELF.bytesWithoutRef.slice(inputValueSize + SELF.propositionBytes.size + inputCreationHeightSize, SELF.bytesWithoutRef.size)
    val outputBytes = selfOutput.bytesWithoutRef.slice(outputValueSize + selfOutput.propositionBytes.size + outputCreationHeightSize, selfOutput.bytesWithoutRef.size)

    val correctValue = selfOutput.value >= SELF.value - 2000000L
    val correctHash = blake2b256(selfOutput.propositionBytes) == contractHash
    val correctBytes = inputBytes == outputBytes

    allOf(Coll(
        correctValue,
        correctHash,
        correctBytes,
        anyOf(Coll(
            creationDifference >= 504000,
            SELF.propositionBytes != selfOutput.propositionBytes
        ))
    ))
}