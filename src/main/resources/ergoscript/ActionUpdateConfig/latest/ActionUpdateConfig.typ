#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "ActionUpdateConfig",
  version: "latest",
  summary: "This action ensures that if the related proposal passes that the dao config gets updated accordingly",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "imPaideiaDaoProposalTokenId", description: "Token ID of the dao proposal tokens", type: "Coll[Byte]", default: "")),
  registers: (
    "R4": (description: [Long values with the following use:
    0. Index of the related proposal
    1. Option in the related proposal this action depends on
    3. Time (in ms) this action is activated
    ], type: "Coll[Long]"),
    "R5": (description: "List of config keys that need to be deleted", type: "Coll[Coll[Byte]]"),
    "R6": (description: "List of values that need to be updated", type: "Coll[(Coll[Byte], Coll[Byte])]"),
    "R7": (description: "List of values that need to be inserted", type: "Coll[(Coll[Byte], Coll[Byte])]")
  ),
  assets: ((
    name: "{DAO Name} Action",
    amount: "1"
  ),),
  transactions: (
    (
      name: "UpdateConfig",
      description: "In this transaction the DAO config utxo AVLTree is updated according to a passed proposal",
      contextVars: (
        (index: "0",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for deleting DAO Config values"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for updating DAO Config values"),
        (index: "3",
        type: "Coll[Byte]",
        description: "Proof for inserting DAO Config values"),
      ),
      configs: ((
        name: "im.paideia.contracts.config",
        type: "PaideiaContractSignature",
        description: "Signature of the config contract in use by this dao"
      ),),
      dataInputs: ((
        name: "Proposal",
        description: "Proposal this action belongs to"
      ),),
      inputs: ((
        name: "DAO Config",
        description: "The current Config utxo for this DAO"
      ),(
        name: "ActionUpdateConfig",
        description: "The utxo ensuring proper execution of the update config action"
      )),
      outputs: ((
        name: "DAO Config",
        description: "Config utxo with the updated config for this DAO"
      ),(
        name: "Miner",
        description: "Miner fee"
      )),
      conditions: (
        "DAO Config input is correct",
        "Proposal data input is correct",
        "Activation time has passed",
        "Action token is burned",
        "Correct config output",
        "No extra outputs are generated",
        "No extra tokens are burned"
      )
    ),
  ),
)

#render(contractDef)