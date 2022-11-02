package im.paideia.staking

import org.ergoplatform.appkit.ContextVar
import io.getblok.getblok_plasma.ByteConversion
import org.ergoplatform.appkit.ErgoValue
import io.getblok.getblok_plasma.collections.ProvenResult
import special.collection.Coll
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ErgoType

class StakingContextVars(val contextVars: List[ContextVar])

object StakingContextVars {
    val STAKE = ErgoValue.of(0.toByte)
    val CHANGE_STAKE = ErgoValue.of(1.toByte)
    val UNSTAKE = ErgoValue.of(2.toByte)
    val SNAPSHOT = ErgoValue.of(3.toByte)
    val COMPOUND = ErgoValue.of(4.toByte)
    val PROFIT_SHARE = ErgoValue.of(5.toByte)

    val dummyKey: String = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"
    
    def stake(stakingKey: String, stakeRecord: StakeRecord, result: ProvenResult[StakeRecord]): StakingContextVars = {
        val operations = ErgoValue.of(Array[(Coll[java.lang.Byte],Coll[java.lang.Byte])](ErgoValue.pairOf(
            ErgoValue.of(ByteConversion.convertsId.convertToBytes(ErgoId.create(stakingKey))),
            ErgoValue.of(StakeRecord.stakeRecordConversion.convertToBytes(stakeRecord))
            ).getValue),ErgoType.pairType(ErgoType.collType(ErgoType.byteType()),ErgoType.collType(ErgoType.byteType())))
        new StakingContextVars(List[ContextVar](
            new ContextVar(0.toByte,STAKE),
            new ContextVar(1.toByte,operations),
            new ContextVar(2.toByte,result.proof.ergoValue),
            new ContextVar(3.toByte,ErgoValue.of(Array[Byte]())),
            new ContextVar(4.toByte,ErgoValue.of(Array[Byte]()))
        ))
    }

    def emit: StakingContextVars = { 
        val operations = ErgoValue.of(Array[(Coll[java.lang.Byte],Coll[java.lang.Byte])](ErgoValue.pairOf(
            ErgoValue.of(Array[Byte]()),
            ErgoValue.of(ByteConversion.convertsLongVal.convertToBytes(0L))
            ).getValue),ErgoType.pairType(ErgoType.collType(ErgoType.byteType()),ErgoType.collType(ErgoType.byteType())))
        new StakingContextVars(List[ContextVar](
            new ContextVar(0.toByte,SNAPSHOT),
            new ContextVar(1.toByte,operations),
            new ContextVar(2.toByte,ErgoValue.of(Array[Byte]())),
            new ContextVar(3.toByte,ErgoValue.of(Array[Byte]())),
            new ContextVar(4.toByte,ErgoValue.of(Array[Byte]()))
        ))
    }

    def profitShare: StakingContextVars = { 
        val operations = ErgoValue.of(Array[(Coll[java.lang.Byte],Coll[java.lang.Byte])](ErgoValue.pairOf(
            ErgoValue.of(Array[Byte]()),
            ErgoValue.of(ByteConversion.convertsLongVal.convertToBytes(0L))
            ).getValue),ErgoType.pairType(ErgoType.collType(ErgoType.byteType()),ErgoType.collType(ErgoType.byteType())))
        new StakingContextVars(List[ContextVar](
            new ContextVar(0.toByte,PROFIT_SHARE),
            new ContextVar(1.toByte,operations),
            new ContextVar(2.toByte,ErgoValue.of(Array[Byte]())),
            new ContextVar(3.toByte,ErgoValue.of(Array[Byte]())),
            new ContextVar(4.toByte,ErgoValue.of(Array[Byte]()))
        ))
    }

    def compound(updatedStakes: List[(String,StakeRecord)], proof: ProvenResult[StakeRecord], snapshotProof: ProvenResult[StakeRecord], removeProof: ProvenResult[StakeRecord]): StakingContextVars = {
        val operations = ErgoValue.of(updatedStakes.map((kv: (String,StakeRecord)) => 
            ErgoValue.pairOf(
            ErgoValue.of(ByteConversion.convertsId.convertToBytes(ErgoId.create(kv._1))),
            ErgoValue.of(StakeRecord.stakeRecordConversion.convertToBytes(kv._2))
            ).getValue).toArray,ErgoType.pairType(ErgoType.collType(ErgoType.byteType()),ErgoType.collType(ErgoType.byteType())))
        new StakingContextVars(List[ContextVar](
            new ContextVar(0.toByte,COMPOUND),
            new ContextVar(1.toByte,operations),
            new ContextVar(2.toByte,proof.proof.ergoValue),
            new ContextVar(3.toByte,snapshotProof.proof.ergoValue),
            new ContextVar(4.toByte,removeProof.proof.ergoValue)
        ))
    }

    def changeStake(updatedStakes: List[(String,StakeRecord)], proof: ProvenResult[StakeRecord]): StakingContextVars = {
        val operations = ErgoValue.of(updatedStakes.map((kv: (String,StakeRecord)) => 
            ErgoValue.pairOf(
            ErgoValue.of(ByteConversion.convertsId.convertToBytes(ErgoId.create(kv._1))),
            ErgoValue.of(StakeRecord.stakeRecordConversion.convertToBytes(kv._2))
            ).getValue).toArray,ErgoType.pairType(ErgoType.collType(ErgoType.byteType()),ErgoType.collType(ErgoType.byteType())))
        new StakingContextVars(List[ContextVar](
            new ContextVar(1.toByte,CHANGE_STAKE),
            new ContextVar(2.toByte,operations),
            new ContextVar(3.toByte,proof.proof.ergoValue)
        ))
    }

    def unstake(stakingKey: String, proof: ProvenResult[StakeRecord], removeProof: ProvenResult[StakeRecord]): StakingContextVars = {
        val operations = ErgoValue.of(Array[(Coll[java.lang.Byte],Coll[java.lang.Byte])](ErgoValue.pairOf(
            ErgoValue.of(ByteConversion.convertsId.convertToBytes(ErgoId.create(stakingKey))),
            ErgoValue.of(StakeRecord.stakeRecordConversion.convertToBytes(StakeRecord(0L,List(0L))))
            ).getValue),ErgoType.pairType(ErgoType.collType(ErgoType.byteType()),ErgoType.collType(ErgoType.byteType())))
        new StakingContextVars(List[ContextVar](
            new ContextVar(0.toByte,UNSTAKE),
            new ContextVar(1.toByte,operations),
            new ContextVar(2.toByte,proof.proof.ergoValue),
            new ContextVar(3.toByte,removeProof.proof.ergoValue),
            new ContextVar(4.toByte,ErgoValue.of(Array[Byte]()))
        ))
    }
}
