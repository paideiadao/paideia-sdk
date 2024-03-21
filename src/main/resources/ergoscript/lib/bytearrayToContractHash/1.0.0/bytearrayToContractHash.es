def bytearrayToContractHash(configValue: Option[Coll[Byte]]): Coll[Byte] = {
    configValue.get.slice(1,33)
}