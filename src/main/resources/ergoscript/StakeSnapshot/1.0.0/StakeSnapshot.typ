#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "StakeSnapshot",
  version: "1.0.0",
  summary: "This contract is a companion contract to the main stake contract. It ensures snapshots are created correctly at the right time.",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "stakeStateTokenId", description: "Token ID of the stake state nft", type: "Coll[Byte]", default: ""),),
  registers: (),
  assets: (),
  transactions: (
    (
      name: "StakeSnapshot",
      description: "In this transaction 1 or more SplitProfit boxes are handled by splitting them between the treasury and the stake state",
      contextVars: (
        (index: "0",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
      ),
      configs: ((
        name: "im.paideia.contracts.staking.snapshot",
        type: "PaideiaContractSignature",
        description: "Signature of the stake snapshot contract"
      ),(
        name: "im.paideia.staking.emission.amount",
        type: "Long",
        description: "Amount of governance tokens to be distributed each staking cycle from the treasury to the stakers"
      ),(
        name: "im.paideia.staking.emission.delay",
        type: "Long",
        description: "Rewards are delayed by this number of staking cycles"
      ),(
        name: "im.paideia.staking.cyclelength",
        type: "Long",
        description: "Length (in ms) of a staking cycle"
      ),(
        name: "im.paideia.staking.weight.pureparticipation",
        type: "Byte",
        description: "Percentage pure participation counts towards earning rewards"
      ),(
        name: "im.paideia.staking.weight.participation",
        type: "Byte",
        description: "Percentage participation counts towards earning rewards"
      ),(
        name: "im.paideia.dao.tokenid",
        type: "Coll[Byte]",
        description: "Governance Token ID of the DAO"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),),
      inputs: ((
        name: "StakeState",
        description: "The utxo containing the staking state"
      ),(
        name: "StakeSnapshot",
        description: "The utxo ensuring proper execution of calculating the compound"
      ),),
      outputs: ((
        name: "StakeState",
        description: "The utxo containing the updated staking state"
      ),(
        name: "StakeSnapshot",
        description: "A copy of the input utxo"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),),
      conditions: (
        "DAO Config input is correct",
        "Stake state input is correct",
        "The new snapshot matches current state and is added to end of snapshot queue",
        "Snapshot queue pop done correctly",
        "Snapshot queue matches emission delay",
        "Profit is reset for the next cycle",
        "Time for next snapshot is set according to cycle length",
        "Snapshot is not taken too early",
        "Register values not relevant to this action in the stake state are unchanged"
      )
    ),
  ),
)

#render(contractDef)