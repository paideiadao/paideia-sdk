#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "StakeCompound",
  version: "1.0.0",
  summary: "This contract is a companion contract to the main stake contract. It ensures compounding the rewards and profit share is done properly.",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "stakeStateTokenId", description: "Token ID of the stake state nft", type: "Coll[Byte]", default: ""),),
  registers: (),
  assets: (),
  transactions: (
    (
      name: "StakeCompound",
      description: "In this transaction 1 or more stakerecords is updated with rewards according to their current stakerecord",
      contextVars: (
        (index: "0",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
        (index: "1",
        type: "Coll[(Coll[Byte],Coll[Byte])]",
        description: "AVLTree update operations to be performed"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for fetching the current stakerecords"),
        (index: "3",
        type: "Coll[Byte]",
        description: "Proof for fetching snapshot data related to the stakers being updated"),
        (index: "4",
        type: "Coll[Byte]",
        description: "Proof for removing the stakers being updated from the list of stakers that need to be updated"),
        (index: "5",
        type: "Coll[Byte]",
        description: "Proof for fetching snapshot governance participation data related to the stakers being updated"),
        (index: "6",
        type: "Coll[Byte]",
        description: "Proof for performing the update operations"),
      ),
      configs: ((
        name: "im.paideia.contracts.staking.compound",
        type: "PaideiaContractSignature",
        description: "Signature of the stake compound contract"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),),
      inputs: ((
        name: "StakeState",
        description: "The utxo containing the staking state"
      ),(
        name: "StakeCompound",
        description: "The utxo ensuring proper execution of calculating the compound"
      ),),
      outputs: ((
        name: "StakeState",
        description: "The utxo containing the updated staking state"
      ),(
        name: "StakeCompound",
        description: "A copy of the input utxo"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),),
      conditions: (
        "DAO Config input is correct",
        "Stake state input is correct",
        "The rewards provided in the update operations match expectations",
        "Staked token amounts are correct in the output",
        "Snapshot is updated correctly by removing handled stakers",
        "New stake state AVLTree digest matches expectations",
        "Stake compound is copied into new utxo",
        "Prevent spam attack by ensuring a minimum amount of stakers is handled, or this is the last batch of stakers to be handled",
        "The current profit in the stake state is updated according to distributed profit",
        "Register values not relevant to this action in the stake state are unchanged"
      )
    ),
  ),
)

#render(contractDef)