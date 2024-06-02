#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "ActionSendFundBasic",
  version: "latest",
  summary: "This action ensures that if the related proposal passes that the treasury sends funds to the outputs as defined at the time of proposal creation. Any change is sent back to the treasury. If this action is to be repeated a copy is part of the output with 1 repeat less.",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "imPaideiaDaoProposalTokenId", description: "Token ID of the dao proposal tokens", type: "Coll[Byte]", default: "")),
  registers: (
    "R4": (description: [Long values with the following use:
    0. Index of the related proposal
    1. Option in the related proposal this action depends on
    2. Number of repeats remaining for this action
    3. Time (in ms) this action is activated
    4. Delay (in ms) for repeat], type: "Coll[Long]"),
    "R5": (description: "The funds that are sent from the treasury should be placed in output boxes following the boxes in this register", type: "Coll[Box]")
  ),
  assets: ((
    name: "{DAO Name} Action",
    amount: "1"
  ),),
  transactions: (
    (
      name: "SendFundsBasic",
      description: "In this transaction funds are sent from the DAO treasury according to a passed proposal",
      contextVars: (
        (index: "0",
        type: "Coll[Byte]",
        description: "Proof for retrieving dao config variables"),
      ),
      configs: ((
        name: "im.paideia.contracts.treasury",
        type: "PaideiaContractSignature",
        description: "Signature of the treasury contract in use by this dao"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo with AVLTree to extract config values from"
      ),(
        name: "Proposal",
        description: "Proposal this action belongs to"
      )),
      inputs: ((
        name: "Treasury",
        description: "1 or more treasury utxos"
      ),(
        name: "ActionSendFundsBasic",
        description: "The utxo ensuring proper execution of basic send funds action"
      )),
      outputs: ((
        name: "Target",
        description: "1 or more target boxes as defined in the action"
      ),(
        name: "ActionSendFundsBasic",
        description: "Optional in case of repeating action"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),(
        name: "Treasury",
        description: "Change goes back to treasury"
      )),
      conditions: (
        "DAO Config data input is correct",
        "Proposal data input is correct",
        "Activation time has passed",
        "Action is repeated or the action token is burned",
        "The outputs match the boxes defined in the action",
        "No extra outputs are generated",
        "No tokens are burned",
        "Change is sent back to treasury",
        "Miner fee is flexible but capped"
      )
    ),
  ),
)

#render(contractDef)