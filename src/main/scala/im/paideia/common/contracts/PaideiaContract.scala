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

class PaideiaContract(_contractSignature: PaideiaContractSignature) {
    val utxos: Set[String] = Set[String]()
    val mutxos: Set[String] = Set[String]()
    val mspent: Set[String] = Set[String]()

    val boxes: HashMap[String,ErgoTransactionOutput] = HashMap[String,ErgoTransactionOutput]()

    def ergoScript: String = Source.fromResource("ergoscript/" + getClass.getSimpleName + "/" + _contractSignature.version + "/" + getClass.getSimpleName + ".es").mkString
    def ergoTree: Values.ErgoTree = {
        JavaHelpers.compile(new java.util.HashMap[String,Object](),ergoScript,_contractSignature.networkType.networkPrefix)
    }
    def contract: ErgoContract = new ErgoTreeContract(ergoTree, _contractSignature.networkType)

    def contractSignature: PaideiaContractSignature = PaideiaContractSignature(
        getClass().getCanonicalName(),
        _contractSignature.version,
        _contractSignature.networkType,
        Blake2b256(ergoTree.bytes).array,
        _contractSignature.wrappedContract
    )

    def spendBox(boxId: String, mempool: Boolean): Boolean = {
        if (mempool) {
            mspent.add(boxId)
        } else {
            boxes -= boxId
            utxos.remove(boxId) || mspent.remove(boxId)
        }
    }

    def newBox(box: ErgoTransactionOutput, mempool: Boolean): Boolean = {
        if (mempool) {
            boxes(box.getBoxId()) = box
            mutxos.add(box.getBoxId())
        } else {
            boxes(box.getBoxId()) = box
            utxos.add(box.getBoxId()) || mutxos.remove(box.getBoxId())
        }
    }

    def getUtxoSet: Set[String] = utxos ++ mutxos -- mspent

    def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        event match {
            case te: TransactionEvent => {
                val handledInputs = te.tx.getInputs().asScala.map{(input: ErgoTransactionInput) => spendBox(input.getBoxId(),te.mempool)}
                val handledOutputs = te.tx.getOutputs().asScala.map{(output: ErgoTransactionOutput) => if (output.getErgoTree == ergoTree.bytesHex) newBox(output,te.mempool) else false}
                if (handledInputs.exists(identity) || handledOutputs.exists(identity)) PaideiaEventResponse(1) else PaideiaEventResponse(0)
            }
            case be: BlockEvent => {
                be.block.getBlockTransactions().getTransactions().asScala.map{(tx: ErgoTransaction) => handleEvent(TransactionEvent(false,tx))}.max          
            }
        }
    }
}
