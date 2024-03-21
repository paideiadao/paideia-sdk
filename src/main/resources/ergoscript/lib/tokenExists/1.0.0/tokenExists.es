def tokenExists(params: (Coll[Box], Coll[Byte])): Boolean = {
    val boxes = params._1
    val tokenId = params._2
    boxes.exists{
            (b: Box) =>
            b.tokens.exists{
                (token: (Coll[Byte],Long)) =>
                token._1 == SELF.tokens(0)._1
            }
        }
}