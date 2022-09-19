package im.paideia

import scala.collection.mutable.HashMap

class DAO(_key: String) {
  val _config: DAOConfig = DAOConfig(new HashMap[DAOConfigKey,DAOConfigValue[_]]())

  def key: String = _key
}
