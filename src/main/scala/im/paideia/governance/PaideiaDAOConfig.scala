package im.paideia.common

import im.paideia.DAOConfig
import im.paideia.util.Env

object PaideiaDAOConfig {
    def apply: DAOConfig = DAOConfig.test
}
