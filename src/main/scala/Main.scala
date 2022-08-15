import im.paideia.staking.{StakingState,StakingConfig}
import im.paideia.staking.TotalStakingState

object Main extends App {
    val config = StakingConfig.test
    val state = TotalStakingState(config, 0L)
    val testKey = "1ad2b4882d0dd8f1b8fd93f1c08a0aff585725c3405661aaa2b50cd305af090c"
    val testKey2 = "2ad2b4882d0dd8f1b8fd93f1c08a0aff585725c3405661aaa2b50cd305af090c"
    state.stake(testKey,100L)
    state.stake(testKey2,200)
    println(state)
    state.emit(System.currentTimeMillis)
    state.emit(System.currentTimeMillis)
    println(state)
    state.emit(System.currentTimeMillis)
    state.emit(System.currentTimeMillis)
    state.compound(50)
    println(state)
    state.emit(System.currentTimeMillis)
    state.compound(50)
    println(state)
    state.unstake(testKey2,state.getStake(testKey2))
    state.emit(System.currentTimeMillis)
    state.compound(50)
    println(state)
    state.emit(System.currentTimeMillis)
    state.compound(50)
    println(state)
}
