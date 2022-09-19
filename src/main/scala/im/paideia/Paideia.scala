package im.paideia

import scala.collection.mutable.HashMap

class Paideia() {
    val _daoMap : HashMap[String,DAO] = HashMap[String,DAO]()
    def addDAO(dao: DAO): Unit = _daoMap.put(dao.key,dao)
}
