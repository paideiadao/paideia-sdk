def filterByHash(params: (Coll[Box], Coll[Byte])): Coll[Box] = {
    val boxes = params._1
    val hash = params._2
    boxes.filter({(b: Box) => blake2b256(b.propositionBytes) == hash})
}

def filterByTokenId(params: (Coll[Box], Coll[Byte])): Coll[Box] = {
    val boxes = params._1
    val tokenId = params._2
    boxes.filter{
        (b: Box) =>
            b.tokens.exists{
                (token: (Coll[Byte],Long)) =>
                token._1 == tokenId
            }
    }
}

def ergInBoxes(boxes: Coll[Box]): Long = {
    boxes.fold(0L, {
            (z: Long, b: Box) => 
            z + b.value
        })
}

def tokenExists(params: (Coll[Box], Coll[Byte])): Boolean = {
    val boxes = params._1
    val tokenId = params._2
    boxes.exists{
            (b: Box) =>
            b.tokens.exists{
                (token: (Coll[Byte],Long)) =>
                token._1 == tokenId
            }
        }
}

def tokensInBoxes(params: (Coll[Box], Coll[Byte])): Long = {
    val boxes = params._1
    val tokenId = params._2
    boxes.flatMap{(b: Box) => b.tokens}
        .fold(0L, {
            (z: Long, token: (Coll[Byte], Long)) => 
            z + (if (token._1 == tokenId) token._2 else 0L)
        })
}

def tokensInBoxesAll(boxes: Coll[Box]): Long = 
    boxes.flatMap{(b: Box) => b.tokens}.fold(0L, {
        (z: Long, token: (Coll[Byte], Long)) =>
        z + token._2
    })