package im.paideia.staking

import org.ergoplatform.appkit.ContextVar
import io.getblok.getblok_plasma.ByteConversion
import org.ergoplatform.appkit.ErgoValue
import io.getblok.getblok_plasma.collections.ProvenResult
import special.collection.Coll
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ErgoType
import im.paideia.governance.VoteRecord

case class StakingContextVars(
  stakingStateContextVars: List[ContextVar],
  companionContextVars: List[ContextVar]
)

object StakingContextVars {
  val STAKE        = ErgoValue.of(0.toByte)
  val CHANGE_STAKE = ErgoValue.of(1.toByte)
  val UNSTAKE      = ErgoValue.of(2.toByte)
  val SNAPSHOT     = ErgoValue.of(3.toByte)
  val COMPOUND     = ErgoValue.of(4.toByte)
  val PROFIT_SHARE = ErgoValue.of(5.toByte)
  val VOTE         = ErgoValue.of(6.toByte)

  val dummyKey: String =
    "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d"

  def stake(
    stakingKey: String,
    stakeRecord: StakeRecord,
    stakeResult: ProvenResult[StakeRecord]
  ): StakingContextVars = {
    val stakeOperations = ErgoValue.of(
      Array[(Coll[java.lang.Byte], Coll[java.lang.Byte])](
        ErgoValue
          .pairOf(
            ErgoValue.of(
              ByteConversion.convertsId.convertToBytes(ErgoId.create(stakingKey))
            ),
            ErgoValue.of(StakeRecord.stakeRecordConversion.convertToBytes(stakeRecord))
          )
          .getValue
      ),
      ErgoType.pairType(
        ErgoType.collType(ErgoType.byteType()),
        ErgoType.collType(ErgoType.byteType())
      )
    )
    StakingContextVars(
      List(
        new ContextVar(1.toByte, STAKE)
      ),
      List(
        new ContextVar(1.toByte, stakeOperations),
        new ContextVar(2.toByte, stakeResult.proof.ergoValue)
      )
    )
  }

  def emit: StakingContextVars = {
    StakingContextVars(
      List(
        new ContextVar(1.toByte, SNAPSHOT)
      ),
      List[ContextVar](
      )
    )
  }

  def profitShare: StakingContextVars = {
    StakingContextVars(
      List(
        new ContextVar(1.toByte, PROFIT_SHARE)
      ),
      List[ContextVar](
      )
    )
  }

  def compound(
    updatedStakes: List[(String, StakeRecord)],
    proof: ProvenResult[StakeRecord],
    snapshotProof: ProvenResult[StakeRecord],
    removeProof: ProvenResult[StakeRecord]
  ): StakingContextVars = {
    val operations = ErgoValue.of(
      updatedStakes
        .map((kv: (String, StakeRecord)) =>
          ErgoValue
            .pairOf(
              ErgoValue
                .of(ByteConversion.convertsId.convertToBytes(ErgoId.create(kv._1))),
              ErgoValue.of(StakeRecord.stakeRecordConversion.convertToBytes(kv._2))
            )
            .getValue
        )
        .toArray,
      ErgoType.pairType(
        ErgoType.collType(ErgoType.byteType()),
        ErgoType.collType(ErgoType.byteType())
      )
    )
    StakingContextVars(
      List(
        new ContextVar(1.toByte, COMPOUND)
      ),
      List(
        new ContextVar(1.toByte, operations),
        new ContextVar(2.toByte, proof.proof.ergoValue),
        new ContextVar(3.toByte, snapshotProof.proof.ergoValue),
        new ContextVar(4.toByte, removeProof.proof.ergoValue)
      )
    )
  }

  def changeStake(
    updatedStakes: List[(String, StakeRecord)],
    proof: ProvenResult[StakeRecord]
  ): StakingContextVars = {
    val operations = ErgoValue.of(
      updatedStakes
        .map((kv: (String, StakeRecord)) =>
          ErgoValue
            .pairOf(
              ErgoValue
                .of(ByteConversion.convertsId.convertToBytes(ErgoId.create(kv._1))),
              ErgoValue.of(StakeRecord.stakeRecordConversion.convertToBytes(kv._2))
            )
            .getValue
        )
        .toArray,
      ErgoType.pairType(
        ErgoType.collType(ErgoType.byteType()),
        ErgoType.collType(ErgoType.byteType())
      )
    )
    StakingContextVars(
      List(
        new ContextVar(1.toByte, CHANGE_STAKE)
      ),
      List(
        new ContextVar(1.toByte, operations),
        new ContextVar(2.toByte, proof.proof.ergoValue)
      )
    )
  }

  def vote(
    voteProof: ProvenResult[VoteRecord],
    stakeProof: ProvenResult[StakeRecord],
    participationProof: ProvenResult[ParticipationRecord],
    updatedStakeProof: ProvenResult[StakeRecord],
    updatedParticipationProof: ProvenResult[ParticipationRecord]
  ): StakingContextVars = {
    StakingContextVars(
      List(
        new ContextVar(1.toByte, VOTE)
      ),
      List(
        new ContextVar(1.toByte, voteProof.proof.ergoValue),
        new ContextVar(2.toByte, stakeProof.proof.ergoValue),
        new ContextVar(3.toByte, updatedStakeProof.proof.ergoValue),
        new ContextVar(4.toByte, participationProof.proof.ergoValue),
        new ContextVar(5.toByte, updatedParticipationProof.proof.ergoValue)
      )
    )
  }

  def unstake(
    stakingKey: String,
    proof: ProvenResult[StakeRecord],
    removeProof: ProvenResult[StakeRecord]
  ): StakingContextVars = {
    val stakeRec = proof.response(0).get
    stakeRec.clear
    val operations = ErgoValue.of(
      Array[(Coll[java.lang.Byte], Coll[java.lang.Byte])](
        ErgoValue
          .pairOf(
            ErgoValue.of(
              ByteConversion.convertsId.convertToBytes(ErgoId.create(stakingKey))
            ),
            ErgoValue.of(
              StakeRecord.stakeRecordConversion
                .convertToBytes(stakeRec)
            )
          )
          .getValue
      ),
      ErgoType.pairType(
        ErgoType.collType(ErgoType.byteType()),
        ErgoType.collType(ErgoType.byteType())
      )
    )
    StakingContextVars(
      List(
        new ContextVar(1.toByte, UNSTAKE)
      ),
      List(
        new ContextVar(1.toByte, operations),
        new ContextVar(2.toByte, proof.proof.ergoValue),
        new ContextVar(3.toByte, removeProof.proof.ergoValue)
      )
    )
  }
}
