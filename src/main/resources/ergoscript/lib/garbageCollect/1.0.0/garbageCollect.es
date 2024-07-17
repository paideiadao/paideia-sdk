#import lib/box/1.0.0/box.es;

def garbageCollect(tokensToBurn: Coll[Coll[Byte]]): Boolean = {
    val timePassed: Int = CONTEXT.preHeader.height - SELF.creationInfo._1
    val notComposed: Boolean = INPUTS.size == 1
    val enoughTimePassed: Boolean = timePassed > 788400
    val tokensBurned: Boolean = tokensToBurn.forall{(tokenId: Coll[Byte]) => !(tokenExists((OUTPUTS, tokenId)))}
    allOf(Coll(
        enoughTimePassed,
        tokensBurned,
        notComposed
    ))
}