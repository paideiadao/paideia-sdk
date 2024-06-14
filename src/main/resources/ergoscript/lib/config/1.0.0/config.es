def configTree(b: Box): AvlTree = {
    b.R4[AvlTree].get
}

def bytearrayToContractHash(configValue: Option[Coll[Byte]]): Coll[Byte] = {
    configValue.get.slice(1,33)
}

def bytearrayToTokenId(configValue: Option[Coll[Byte]]): Coll[Byte] = {
    configValue.get.slice(6,38)
}