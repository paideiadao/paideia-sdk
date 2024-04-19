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

def bytesWithoutCreationInfo(box: Box): Coll[Byte] = {
    val valueByteSize = vlqByteSize(box.value)
    val creationByteSize = vlqByteSize(box.creationInfo._1.toLong)
    val bytesWR = box.bytesWithoutRef
    bytesWR.slice(0,valueByteSize+box.propositionBytes.size).append(bytesWR.slice(valueByteSize+box.propositionBytes.size+creationByteSize, bytesWR.size))
}