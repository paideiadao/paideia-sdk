package im.paideia

import scala.collection.mutable.HashMap
import scala.collection.mutable.Set
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaActor
import im.paideia.util.Env
import im.paideia.common.PaideiaEvent
import im.paideia.common.PaideiaEventResponse

object Paideia {
    val _daoMap : HashMap[String,DAO] = HashMap[String,DAO]()

    val _actorList : Set[PaideiaActor] = Set(Config)

    def addDAO(dao: DAO): Unit = _daoMap.put(dao.key,dao)

    def initialize: Unit = {
        val paideiaConfig = DAOConfig()

        addDAO(DAO(Env.paideiaDaoKey,paideiaConfig))
    }

    def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        _actorList.map{_.handleEvent(event)}.max
    }

    def addActor(actor: PaideiaActor): Boolean = _actorList.add(actor)
}
