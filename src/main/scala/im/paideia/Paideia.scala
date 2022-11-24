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
import scala.reflect.runtime.{universe => ru}
import im.paideia.common.filtering.FilterNode
import org.ergoplatform.appkit.InputBox
import im.paideia.governance.contracts.ProposalContract
import scorex.crypto.hash.Blake2b256

object Paideia {
    lazy val _daoMap : HashMap[String,DAO] = HashMap[String,DAO]()

    lazy val _actorList : HashMap[String,PaideiaActor] = HashMap[String,PaideiaActor]()

    def addDAO(dao: DAO): Unit = _daoMap.put(dao.key,dao)

    def getDAO(key: String): DAO = _daoMap(key)

    def initialize: Unit = {
        val paideiaConfig = DAOConfig()

        addDAO(DAO(Env.paideiaDaoKey,paideiaConfig))
    }

    def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        PaideiaEventResponse.merge(_actorList.values.map{
            _.handleEvent(event)
        }.toList)
    }

    def getActor[T <: PaideiaActor](className: String): PaideiaActor = _actorList(className).asInstanceOf[T]

    def instantiateActor(contractSignature: PaideiaContractSignature) = {
        if (!_actorList.contains(contractSignature.className)) {
            val m = ru.runtimeMirror(getClass.getClassLoader)
            val inst = m.reflectModule(m.staticModule(contractSignature.className)).instance
            inst match {
                case pa: PaideiaActor => _actorList.put(contractSignature.className,pa)
            } 
        }
    }

    def getBox(boxFilter: FilterNode): List[InputBox] = {
        _actorList.values.toList.flatMap(_.getBox(boxFilter))
    }

    def getConfig(daoKey: String): DAOConfig = _daoMap(daoKey).config

    def getProposalContract(box: InputBox): ProposalContract = {
        val contractHash = Blake2b256(box.getErgoTree().bytes).array.toList
        _actorList.values.find(_.getProposalContract(contractHash).isSuccess).get.getProposalContract(contractHash).get
    }
}
