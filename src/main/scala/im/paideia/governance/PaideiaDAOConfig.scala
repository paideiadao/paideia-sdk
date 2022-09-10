package im.paideia.common

import im.paideia.governance.DAOConfig
import im.paideia.util.Env

object PaideiaDAOConfig {
    def apply: DAOConfig = new DAOConfig(Env.configTokenId)
}
