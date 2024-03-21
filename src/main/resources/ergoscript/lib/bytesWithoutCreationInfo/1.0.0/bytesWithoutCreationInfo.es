#import lib/vlqByteSize/1.0.0/vlqByteSize.es;

def bytesWithoutCreationInfo(box: Box): Coll[Byte] = {
    val valueByteSize = vlqByteSize(box.value)
    val creationByteSize = vlqByteSize(box.creationInfo._1.toLong)
    val bytesWR = box.bytesWithoutRef
    bytesWR.slice(0,valueByteSize+box.propositionBytes.size).append(bytesWR.slice(valueByteSize+box.propositionBytes.size+creationByteSize, bytesWR.size))
}