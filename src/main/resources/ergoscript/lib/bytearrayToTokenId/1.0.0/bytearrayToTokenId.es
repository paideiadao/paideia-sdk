def bytearrayToTokenId(configValue: Option[Coll[Byte]]): Coll[Byte] = {
    configValue.get.slice(6,38)
}