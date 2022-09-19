package im.paideia

import im.paideia.util.Util
import scala.collection.mutable.HashMap

case class DAOConfig(
   val _config: HashMap[DAOConfigKey,DAOConfigValue[_]]
) {
    def apply[T](key: String) = _config.get(DAOConfigKey(key)).get.value.asInstanceOf[T]
}

object DAOConfig {
    def test: DAOConfig = 
        new DAOConfig(
            new HashMap[DAOConfigKey,DAOConfigValue[_]]()+=((DAOConfigKey("configTokenId"),DAOConfigValue(Util.randomKey)))
            )
}
