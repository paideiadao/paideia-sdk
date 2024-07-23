#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "DAOOrigin",
  version: "1.0.0",
  summary: "* Holds the dao token verifying it as a genuine Paideia DAO and controls the proposal/action creation process.",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "paideiaDaoKey", description: "Token ID of the Paideia dao nft", type: "Coll[Byte]", default: ""),
    (name: "paideiaTokenId", description: "Token ID of the Paideia token", type: "Coll[Byte]", default: ""),
    (name: "stakeStateTokenId", description: "Token ID of the stake state nft", type: "Coll[Byte]", default: "")),
  registers: (
  ),
  assets: ((
    name: "Paideia DAO",
    amount: "1"
  ),(
    name: "{DAO Name} Proposal",
    amount: "Max long (initially)"
  ),(
    name: "{DAO Name} Action",
    amount: "Max long (initially)"
  ),),
  transactions: (
    (
      name: "Create proposal",
      description: "In this transaction a proposal is created for the DAO",
      contextVars: (
        (index: "0",
        type: "Byte",
        description: "Transaction type"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for retrieving current Paideia DAO Config values"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
        (index: "3",
        type: "Box",
        description: "Proposal box to be created"),
        (index: "4",
        type: "Coll[Box]",
        description: "Action boxes to be created"),
        (index: "5",
        type: "Coll[Byte]",
        description: "Token ID of the stake key creating the proposal"),
        (index: "6",
        type: "Coll[Byte]",
        description: "Proof for fetching the current stake")
      ),
      configs: ((
        name: "im.paideia.fees.createproposal.paideia",
        type: "Long",
        description: "Fee for creating a proposal"
      ),(
        name: "im.paideia.contracts.dao",
        type: "PaideiaContractSignature",
        description: "Signature of dao origin contract"
      ),(
        name: "im.paideia.dao.min.proposal.time",
        type: "Long",
        description: "Minimum time for a proposal to last"
      ),(
        name: "im.paideia.dao.min.stake.proposal",
        type: "Long",
        description: "Minimum stake needed for creating a proposal"
      ),(
        name: "im.paideia.contracts.proposal.{proposal ergotree bytes}",
        type: "PaideiaContractSignature",
        description: "Signature of whitelisted proposal"
      ),(
        name: "im.paideia.contracts.action.{action ergotree bytes}",
        type: "PaideiaContractSignature",
        description: "Signature of the action contract"
      ),),
      dataInputs: ((
        name: "Config",
        description: "Config of this DAO"
      ),(
        name: "Paideia Config",
        description: "Config of Paideia DAO"
      ),(
        name: "Stake State",
        description: "Stake state of this DAO"
      ),),
      inputs: ((
        name: "DAO Origin",
        description: "The DAO origin of this dao"
      ),(
        name: "User",
        description: "The utxo(s) paying for proposal creation"
      )),
      outputs: ((
        name: "DAO Origin",
        description: "The DAO origin of this dao"
      ),(
        name: "Proposal",
        description: "The newly created proposal"
      ),(
        name: "Action",
        description: "0 or more action boxes"
      ),(
        name: "User",
        description: "Change to the user (including stake key)"
      ),(
        name: "Miner",
        description: "Miner fee"
      )),
      conditions: (
        "DAO Config input is correct",
        "Paideia DAO config is correct",
        "Stake state is correct",
        "Proposal output is correct",
        "Action output is correct",
        "User has enough staked",
        "Proposal does not end too soon",
      )
    ),
  ),
)

#render(contractDef)