def tokensInBoxes(params: (Coll[Box], Coll[Byte])): Long = {
    val boxes = params._1
    val tokenId = params._2
    boxes.flatMap{(b: Box) => b.tokens}
        .fold(0L, {
            (z: Long, token: (Coll[Byte], Long)) => 
            z + (if (token._1 == tokenId) token._2 else 0L)
        })
}