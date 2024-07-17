package im.paideia.staking

import org.ergoplatform.appkit.ContextVar
import work.lithos.plasma.ByteConversion
import org.ergoplatform.appkit.ErgoValue
import work.lithos.plasma.collections.ProvenResult
import sigma.Coll
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.appkit.ErgoType
import im.paideia.governance.VoteRecord
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigma.Colls
import scorex.util.encode.Base16
import im.paideia.util.TxTypes._

case class StakingContextVars(
  stakingStateContextVars: List[ContextVar],
  companionContextVars: List[ContextVar]
)

object StakingContextVars {
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
        new ContextVar(0.toByte, STAKE)
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
        new ContextVar(0.toByte, SNAPSHOT)
      ),
      List[ContextVar](
      )
    )
  }

  def profitShare: StakingContextVars = {
    StakingContextVars(
      List(
        new ContextVar(0.toByte, PROFIT_SHARE)
      ),
      List[ContextVar](
      )
    )
  }

  def compound(
    updatedStakes: List[(String, StakeRecord)],
    proof: ProvenResult[StakeRecord],
    snapshotProof: ProvenResult[StakeRecord],
    removeProof: ProvenResult[StakeRecord],
    participationProof: ProvenResult[ParticipationRecord],
    updateStakeProof: ProvenResult[StakeRecord]
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
        new ContextVar(0.toByte, COMPOUND)
      ),
      List(
        new ContextVar(1.toByte, operations),
        new ContextVar(2.toByte, proof.proof.ergoValue),
        new ContextVar(3.toByte, snapshotProof.proof.ergoValue),
        new ContextVar(4.toByte, removeProof.proof.ergoValue),
        new ContextVar(5.toByte, participationProof.proof.ergoValue),
        new ContextVar(6.toByte, updateStakeProof.proof.ergoValue)
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
        new ContextVar(0.toByte, CHANGE_STAKE)
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
    updatedParticipationProof: ProvenResult[ParticipationRecord],
    newStakeRecord: StakeRecord,
    newParticipationRecord: ParticipationRecord,
    castedVote: VoteRecord,
    stakeKey: String
  ): StakingContextVars = {
    StakingContextVars(
      List(
        new ContextVar(0.toByte, VOTE)
      ),
      List(
        new ContextVar(1.toByte, voteProof.proof.ergoValue),
        new ContextVar(2.toByte, stakeProof.proof.ergoValue),
        new ContextVar(3.toByte, updatedStakeProof.proof.ergoValue),
        new ContextVar(4.toByte, participationProof.proof.ergoValue),
        new ContextVar(5.toByte, updatedParticipationProof.proof.ergoValue),
        new ContextVar(
          6.toByte,
          ErgoValueBuilder.buildFor(
            Colls.fromArray(
              StakeRecord.stakeRecordConversion.convertToBytes(newStakeRecord)
            )
          )
        ),
        new ContextVar(
          7.toByte,
          ErgoValueBuilder.buildFor(
            Colls.fromArray(
              ParticipationRecord.participationRecordConversion.convertToBytes(
                newParticipationRecord
              )
            )
          )
        ),
        new ContextVar(
          8.toByte,
          ErgoValueBuilder.buildFor(
            Colls.fromArray(
              VoteRecord.convertsVoteRecord.convertToBytes(
                castedVote
              )
            )
          )
        ),
        new ContextVar(
          9.toByte,
          ErgoValueBuilder.buildFor(
            Colls.fromArray(
              Base16.decode(stakeKey).get
            )
          )
        )
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
    val operations = ErgoValueBuilder.buildFor(
      Colls.fromArray(
        Array(
          Colls.fromArray(
            ByteConversion.convertsId.convertToBytes(ErgoId.create(stakingKey))
          )
        )
      )
    )
    StakingContextVars(
      List(
        new ContextVar(0.toByte, UNSTAKE)
      ),
      List(
        new ContextVar(1.toByte, operations),
        new ContextVar(2.toByte, proof.proof.ergoValue),
        new ContextVar(3.toByte, removeProof.proof.ergoValue)
      )
    )
  }
}
