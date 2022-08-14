import im.paideia.staking.{StakingState,StakingConfig}

object Main extends App {
    val config = StakingConfig.test
    val state = StakingState(config)
    val testKey = "1ad2b4882d0dd8f1b8fd93f1c08a0aff585725c3405661aaa2b50cd305af090c"
    val testKey2 = "2ad2b4882d0dd8f1b8fd93f1c08a0aff585725c3405661aaa2b50cd305af090c"
    state.stake(testKey,100L)
    println(state)
    println(state.getStakes(List[String](testKey)).proof)
    println(state.changeStakes(List[(String,Long)]((testKey,200L))).proof)
    println(state)
    println(state.getStake(testKey))
    val res = state.getStakes(List[String](testKey))
    val res2 = state.getStakes(List[String](testKey2,testKey))
    println(res.proof)
    println(res2.proof)
    println(res2.response)
}
