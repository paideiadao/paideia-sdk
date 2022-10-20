package im.paideia.common.contracts

import scala.io.Source
import sigmastate.Values
import org.ergoplatform.appkit.JavaHelpers
import org.ergoplatform.appkit.ErgoContract
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.NetworkType
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.ErgoValue
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import java.lang.{Byte => JByte}
import im.paideia.DAOConfigValue
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import scala.collection.mutable.Set
import im.paideia.common.PaideiaEvent
import im.paideia.common.PaideiaEventResponse
import im.paideia.common.TransactionEvent
import org.ergoplatform.restapi.client.ErgoTransactionInput
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.common.BlockEvent
import org.ergoplatform.restapi.client.ErgoTransaction
import scala.collection.mutable.HashMap
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.common.filtering.FilterNode

class PaideiaContract(_contractSignature: PaideiaContractSignature) {
    val utxos: Set[String] = Set[String]()
    val mutxos: Set[String] = Set[String]()
    val mspent: Set[String] = Set[String]()

    val boxes: HashMap[String,InputBox] = HashMap[String,InputBox]()

    def ergoScript: String = Source.fromResource("ergoscript/" + getClass.getSimpleName + "/" + _contractSignature.version + "/" + getClass.getSimpleName + ".es").mkString
    def ergoTree: Values.ErgoTree = {
        JavaHelpers.compile(constants,ergoScript,_contractSignature.networkType.networkPrefix)
    }
    def contract: ErgoContract = new ErgoTreeContract(ergoTree, _contractSignature.networkType)

    def constants: java.util.HashMap[String,Object] = new java.util.HashMap[String,Object]()

    def contractSignature: PaideiaContractSignature = PaideiaContractSignature(
        getClass().getCanonicalName(),
        _contractSignature.version,
        _contractSignature.networkType,
        Blake2b256(ergoTree.bytes).array.toList,
        _contractSignature.daoKey
    )

    def spendBox(boxId: String, mempool: Boolean): Boolean = {
        if (mempool) {
            mspent.add(boxId)
        } else {
            boxes -= boxId
            utxos.remove(boxId) || mspent.remove(boxId)
        }
    }

    def newBox(box: InputBox, mempool: Boolean): Boolean = {
        if (mempool) {
            boxes(box.getId().toString()) = box
            mutxos.add(box.getId().toString())
        } else {
            boxes(box.getId().toString()) = box
            utxos.add(box.getId().toString()) || mutxos.remove(box.getId().toString())
        }
    }

    def getUtxoSet: Set[String] = utxos ++ mutxos -- mspent

    def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        event match {
            case te: TransactionEvent => {
                val handledInputs = te.tx.getInputs().asScala.map{(input: ErgoTransactionInput) => spendBox(input.getBoxId(),te.mempool)}
                val handledOutputs = te.tx.getOutputs().asScala.map{(output: ErgoTransactionOutput) => if (output.getErgoTree == ergoTree.bytesHex) newBox(new InputBoxImpl(output),te.mempool) else false}
                if (handledInputs.exists(identity) || handledOutputs.exists(identity)) PaideiaEventResponse(1) else PaideiaEventResponse(0)
            }
            case be: BlockEvent => {
                PaideiaEventResponse.merge(be.block.getBlockTransactions().getTransactions().asScala.map{(tx: ErgoTransaction) => handleEvent(TransactionEvent(event.ctx,false,tx))}.toList)     
            }
        }
    }

    def getBox(boxFilter: FilterNode): List[InputBox] = {
        boxes.values.filter(boxFilter.matchBox(_)).toList
    }
}
