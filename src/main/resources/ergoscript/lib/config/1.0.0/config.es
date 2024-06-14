def configTree(b: Box): AvlTree = {
    b.R4[AvlTree].get
}

def bytearrayToContractHash(configValue: Option[Coll[Byte]]): Coll[Byte] = {
    configValue.get.slice(1,33)
}

def bytearrayToTokenId(configValue: Option[Coll[Byte]]): Coll[Byte] = {
    configValue.get.slice(6,38)
}

def bytearrayToLongClamped(params: (Option[Coll[Byte]], (Long, (Long, Long)))): Long = {
    val serialized: Option[Coll[Byte]] = params._1
    val min_val: Long = params._2._1
    val max_val: Long = params._2._2._1
    val default: Long = params._2._2._2

    serialized.map{(serialized_val: Coll[Byte]) => {
        if (serialized_val.size == 9) {
            val long_val = byteArrayToLong(serialized_val.slice(1,9))
            if (long_val < min_val) {
                min_val
            } else {
                if (long_val > max_val) {
                    max_val
                } else {
                    long_val
                }
            }
        } else {
            default
        }
    }}.getOrElse(default)
}