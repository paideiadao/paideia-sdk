#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "StakeVote",
  version: "latest",
  summary: "This contract is a companion contract to the main stake contract. It ensures voting participation is registered correctly",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),),
  registers: (),
  assets: (),
  transactions: (
    (
      name: "Vote",
      description: "In this transaction a vote is cast and the users' participation in the governance process is logged",
      contextVars: (
        (index: "0",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for retrieving current vote from the proposal"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for retrieving current stake record for user"),
        (index: "3",
        type: "Coll[Byte]",
        description: "Proof for updating the stake record for user"),
        (index: "4",
        type: "Coll[Byte]",
        description: "Proof for retrieving current participation record for user"),
        (index: "5",
        type: "Coll[Byte]",
        description: "Proof for updating participation record for user"),
        (index: "6",
        type: "Coll[Byte]",
        description: "New stake record for user"),
        (index: "7",
        type: "Coll[Byte]",
        description: "New participation record for user")
      ),
      configs: ((
        name: "im.paideia.staking.state.tokenid",
        type: "Coll[Byte]",
        description: "Token ID of this DAO's stake state token"
      ),(
        name: "im.paideia.contracts.staking.vote",
        type: "PaideiaContractSignature",
        description: "Signature of the stake vote contract"
      ),(
        name: "im.paideia.dao.proposal.tokenid",
        type: "Long",
        description: "Token ID of the DAO proposal token"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),),
      inputs: ((
        name: "StakeState",
        description: "The utxo containing the staking state"
      ),(
        name: "StakeVote",
        description: "The utxo ensuring proper execution of registering the vote participation"
      ),(
        name: "Proposal",
        description: "The utxo containing the proposal being voted on"
      ),),
      outputs: ((
        name: "StakeState",
        description: "The utxo containing the updated staking state"
      ),(
        name: "StakeVote",
        description: "A copy of the input utxo"
      ),(
        name: "Proposal",
        description: "The utxo containing the proposal with updated vote"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),),
      conditions: (
        "DAO Config input is correct",
        "Stake state input is correct",
        "The proposal being voted on is valid",
        "The stake key is present",
        "Vote totals updated in the stake state",
        "Stake is locked until at least end of the proposal",
        "Register values not relevant to this action in the stake state are unchanged"
      )
    ),
  ),
)

#render(contractDef)