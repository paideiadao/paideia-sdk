def bytearrayToString(configValue: Option[Coll[Byte]]): Coll[Byte] = {
    configValue.get.slice(5,configValue.get.size)
}