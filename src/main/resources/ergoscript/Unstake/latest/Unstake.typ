#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "Unstake",
  version: "latest",
  summary: "This contract is a companion contract to the main stake contract. It ensures properly unstaking assets from the stake state",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "stakeStateTokenId", description: "Token ID of the stake state nft", type: "Coll[Byte]", default: ""),),
  registers: (),
  assets: (),
  transactions: (
    (
      name: "Unstake",
      description: "In this transaction the assets belonging to the user are removed from the stake state",
      contextVars: (
        (index: "0",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
        (index: "1",
        type: "Coll[(Coll[Byte],Coll[Byte])]",
        description: "Operations to perform on stake tree"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for getting current stake"),
        (index: "3",
        type: "Coll[Byte]",
        description: "Proof for removing the stake")
      ),
      configs: ((
        name: "im.paideia.contracts.staking.unstake",
        type: "PaideiaContractSignature",
        description: "Signature of the unstake contract"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),),
      inputs: ((
        name: "StakeState",
        description: "The utxo containing the staking state"
      ),(
        name: "Unstake",
        description: "The utxo ensuring proper execution of unstaking assets"
      )),
      outputs: ((
        name: "StakeState",
        description: "The utxo containing the updated staking state"
      ),(
        name: "Unstake",
        description: "A copy of the input utxo"
      ),(
        name: "User",
        description: "The utxo containing the unstaked assets"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),),
      conditions: (
        "DAO Config input is correct",
        "Stake state input is correct",
        "All governance tokens are unstaked",
        "All erg profit is unstaked",
        "Only one unstake done",
        "AVL Trees updated",
        "Staker count decreased by 1",
        "Register values not relevant to this action in the stake state are unchanged",
        "Stake is not locked"
      )
    ),
  ),
)

#render(contractDef)