#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "Stake",
  version: "1.0.0",
  summary: "This contract is a companion contract to the main stake contract. It ensures creating a new stake is following the rules. For asset and register descriptions see Stake State.",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "stakeStateTokenId", description: "Token ID of the stake state nft", type: "Coll[Byte]", default: ""),),
  registers: (),
  assets: (),
  transactions: (
    (
      name: "Stake",
      description: "In this transaction a new stake is created and as such a new DAO member is created",
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
        name: "im.paideia.contracts.staking.stake",
        type: "PaideiaContractSignature",
        description: "Signature of the stake contract"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),),
      inputs: ((
        name: "StakeState",
        description: "The utxo containing the staking state"
      ),(
        name: "Stake",
        description: "The utxo ensuring proper execution of creating the stake"
      ),(
        name: "User",
        description: "User UTXO(s) containing enough erg and governance tokens to perform the action"
      )),
      outputs: ((
        name: "StakeState",
        description: "The utxo containing the updated staking state"
      ),(
        name: "Stake",
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
        "Correct Stake key is minted",
        "Staked token amounts are correct in the output",
        "Only one operation is performed on the stake state",
        "The digest of the new stake state is correct",
        "The new stake record is empty besides the new stake (0 profit and governance participation",
        "Staker count is updated correctly",
        "Register values not relevant to this action in the stake state are unchanged"
      )
    ),
  ),
)

#render(contractDef)