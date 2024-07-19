#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "ProposalBasic",
  version: "latest",
  summary: "This contract ensures votes are cast fairly and evaluation is done according to the rules as defined by the framework and the dao config",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "paideiaDaoKey", description: "Token ID of the Paideia DAO nft", type: "Coll[Byte]", default: ""),
    (name: "paideiaTokenId", description: "Token ID of the Paideia token", type: "Coll[Byte]", default: ""),
    (name: "stakeStateTokenId", description: "Token ID of the stake state nft", type: "Coll[Byte]", default: "")),
  registers: (
    "R4": (description: [Int values with the following use:
    0. Index of the proposal
    1. Proposal status (-1 active, -2 failed, 0-n option that has passed)
    ], type: "Coll[Int]"),
    "R5": (description: [Long values with the following use:
    0. Ending time of the proposal
    1. Total votes cast
    2-n. Votes cast on each option
    ], type: "Coll[Long]"),
    "R6": (description: "AVLTree holding individual votes (tokenid of stakekey, voterecord)", type: "AVLTree"),
    "R7": (description: "Name of the proposal", type: "Coll[Byte]")
  ),
  assets: ((
    name: "{DAO Name} Proposal",
    amount: "1"
  ),(
    name: "Paideia",
    amount: "Create proposal fee amount"
  ),),
  transactions: (
    (
      name: "Cast vote",
      description: "In this transaction a vote is cast on an active proposal",
      contextVars: (
        (index: "0",
        type: "Byte",
        description: "Transaction type"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for retrieving current vote"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for inserting/updating vote"),
        (index: "3",
        type: "Coll[Byte]",
        description: "Proof for retrieving stake"),
        (index: "4",
        type: "Coll[Byte]",
        description: "Vote to be cast"),
        (index: "5",
        type: "Coll[Byte]",
        description: "Token ID of stake key"),
      ),
      configs: (),
      dataInputs: (),
      inputs: ((
        name: "Stake state",
        description: "Stake state for this dao"
      ),(
        name: "Stake Vote",
        description: "Stake vote logic"
      ),(
        name: "ProposalBasic",
        description: "The proposal to be voted on"
      ),(
        name: "User",
        description: "The utxo(s) the user provides to vote"
      )),
      outputs: ((
        name: "Stake state",
        description: "Stake state for this dao"
      ),(
        name: "Stake Vote",
        description: "Stake vote logic"
      ),(
        name: "ProposalBasic",
        description: "Updated proposal utxo"
      ),(
        name: "User",
        description: "Change returned to user (including stake key)"
      ),(
        name: "Miner",
        description: "Miner fee"
      )),
      conditions: (
        "Stake state is correct",
        "Proposal updated correct",
        "End time not passed",
        "Vote does not exceed staked amount",
      )
    ),(
      name: "Evaluate proposal",
      description: "In this transaction the proposal ends and is evaluated",
      contextVars: (
        (index: "0",
        type: "Byte",
        description: "Transaction type"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for retrieving dao config"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for retrieving paideia dao config"),
        (index: "10",
        type: "(Int, Long)",
        description: "The winning vote"),
      ),
      configs: ((
        name: "im.paideia.dao.quorum",
        type: "Long",
        description: "Quorum needed for proposal to pass"
      ),(
        name: "im.paideia.dao.threshold",
        type: "Long",
        description: "Threshold needed for a proposal option to pass"
      ),(
        name: "im.paideia.fees.createproposal.paideia",
        type: "Long",
        description: "Fee for creating a proposal"
      ),(
        name: "im.paideia.contracts.split.profit",
        type: "PaideiaContractSignature",
        description: "Signature of split profit contract for paideia dao"
      ),),
      dataInputs: ((
        name: "Stake state",
        description: "Stake state for this dao"
      ),(
        name: "DAO Config",
        description: "Config for this dao"
      ),(
        name: "Paideia DAO Config",
        description: "Config for Paideia DAO"
      ),),
      inputs: ((
        name: "ProposalBasic",
        description: "The proposal to be voted on"
      ),),
      outputs: ((
        name: "ProposalBasic",
        description: "Updated proposal utxo"
      ),(
        name: "SplitProfit",
        description: "Proposal creation fee sent to Paideia DAO"
      ),(
        name: "Miner",
        description: "Miner fee"
      )),
      conditions: (
        "Stake state is correct",
        "Config correct",
        "Paideia Config correct",
        "Winning vote correct",
        "End time passed",
      )
    ),
  ),
)

#render(contractDef)