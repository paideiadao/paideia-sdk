def validRefund(params: (Box,(Box, (Coll[Byte], Int)))): Boolean = {
    val inputBox = params._1
    val outputBox = params._2._1
    val propBytes = params._2._2._1
    val minDelay = params._2._2._2
    allOf(Coll(
        outputBox.value >= inputBox.value - 1000000L,
        outputBox.tokens == inputBox.tokens,
        outputBox.propositionBytes == propBytes,
        CONTEXT.preHeader.height >= inputBox.creationInfo._1 + minDelay
    ))
}