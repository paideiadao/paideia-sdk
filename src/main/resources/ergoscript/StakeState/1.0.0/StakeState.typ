#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "StakeState",
  version: "1.0.0",
  summary: "The main state contract. To reduce size and complexity most logic is stored in companion contracts.",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),),
  registers: (
    "R4": (description: [AVLTree values with the following use:
    0. Current stake state
    1. Current participation state
    ], type: "Coll[AVLTree]"),
    "R5": (description: [Long values with the following use:
    0. Next emission time in ms
    1. Total governance tokens Staked
    2. Amount of stakers
    3. Amount of times voted
    4. Amount of voting power used
    5+. Profit accrued], type: "Coll[Long]"),
    "R6": (description: [Long arrays holding the values of past snapshots:
    0. Total governance tokens Staked
    1. Amount of times voted
    2. Amount of voting power used
    3. Pure participation weight
    4. Participation weight], type: "Coll[Coll[Long]]"),
    "R7": (description: [List of tuples of avltree snapshots (stake state, participation)], type: "Coll[(AVLTree, AVLTree)]"),
    "R8": (description: [Profit to be distributed in latest snapshot], type: "Coll[Long]"),
  ),
  assets: ((
    name: "{DAO Name} Stake State",
    amount: "1"
  ),(
    name: "{DAO Governance token}",
    amount: "1+"
  ),),
  transactions: (
    (
      name: "Any stake transaction",
      description: "Almost all logic is handled in companion contracts so remaining logic is simple",
      contextVars: (
        (index: "0",
        type: "Byte",
        description: "Transaction type"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
      ),
      configs: ((
        name: "im.paideia.contracts.staking.state",
        type: "PaideiaContractSignature",
        description: "Signature of the stake state contract"
      ),(
        name: "im.paideia.contracts.staking.*",
        type: "PaideiaContractSignature",
        description: "Signature of the companion contract"
      ),),
      dataInputs: ((
        name: "Config",
        description: "DAO Config"
      ),),
      inputs: ((
        name: "StakeState",
        description: "Utxo belonging to this contract"
      ),(
        name: "Companion contract",
        description: "The utxo holding the companion contract"
      )),
      outputs: ((
        name: "StakeState",
        description: "Utxo belonging to this contract"
      ),(
        name: "Companion contract",
        description: "The utxo holding the companion contract"
      ),(
        name: "Miner",
        description: "Miner fee"
      )),
      conditions: (
        "DAO Config input is correct",
        "Companion contract correct and present",
        "Stake state nft remaining in place",
        "Staked governance tokens remaining in place",
      )
    ),
  ),
)

#render(contractDef)