#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "ChangeStake",
  version: "1.0.0",
  summary: "This contract is a companion contract to the main stake contract. It ensures the stake is changed correctly following the rules. For asset and register descriptions see Stake State. Partial unstake of governance tokens is not allowed because with delayed staking rewards this will result in a penalty for unstaking.",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),),
  registers: (),
  assets: (),
  transactions: (
    (
      name: "ChangeStake",
      description: "In this transaction the stake of a DAO member is increased or secondary profit is removed",
      contextVars: (
        (index: "0",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
        (index: "1",
        type: "Coll[(Coll[Byte],Coll[Byte])]",
        description: "Stake operations to be performed"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for performing the stake operations"),
      ),
      configs: ((
        name: "im.paideia.contracts.staking.changestake",
        type: "PaideiaContractSignature",
        description: "Signature of the change stake contract"
      ),(
        name: "im.paideia.staking.state.tokenid",
        type: "Coll[Byte]",
        description: "Token ID of this DAO's stake state token"
      )),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),),
      inputs: ((
        name: "StakeState",
        description: "The utxo containing the staking state"
      ),(
        name: "ChangeStake",
        description: "The utxo ensuring proper execution of changing the stake"
      ),(
        name: "User",
        description: "User UTXO containing the stake key and enough erg to perform the action"
      )),
      outputs: ((
        name: "StakeState",
        description: "The utxo containing the updated staking state"
      ),(
        name: "ChangeStake",
        description: "A copy of the input utxo"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),(
        name: "User",
        description: "User UTXO containing the stake key and any leftover assets"
      )),
      conditions: (
        "DAO Config input is correct",
        "Stake state input is correct",
        "Stake key is part of the input",
        "Staked token amounts are correct in the output",
        "Only one operation is performed on the stake state",
        "The digest of the new stake state is correct",
        "Erg profit is valid",
        "Correct change stake copy is in output",
        "Register values not relevant to this action in the stake state are unchanged",
        "No partial unstake of governance tokens is performed"
      )
    ),
  ),
)

#render(contractDef)