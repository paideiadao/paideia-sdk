#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "Config",
  version: "latest",
  summary: "This is a long living contract that guards the configuration of the DAO. It can only be spent when the contract is updated, prevent storage rent or when a proposal to change the configuration has passed.",
  parameters: (
    (name: "imPaideiaDaoActionTokenId", description: "Token ID of the dao action token", type: "Coll[Byte]", default: ""),),
  registers: ("R4": (description: [AVL Tree holding the configuration of the DAO], type: "AVLTree"),),
  assets: ((
    name: "{DAO Name} DAO Key",
    amount: "1"
  ),),
  transactions: (
    (
      name: "UpdateConfig",
      description: "In this transaction the configuration of the DAO is altered based on a passed proposal",
      contextVars: (
        (index: "0",
        type: "Byte",
        description: "Transaction type"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
      ),
      configs: ((
        name: "im.paideia.contracts.config",
        type: "PaideiaContractSignature",
        description: "Signature of the config contract"
      ),(
        name: "im.paideia.contracts.action",
        type: "PaideiaContractSignature",
        description: "Signature of the whitelisted Action contract"
      )),
      dataInputs: ((
        name: "Proposal",
        description: "Verifying the proposal has passed"
      ),),
      inputs: ((
        name: "Config",
        description: "The utxo containing the current config"
      ),(
        name: "ActionUpdateConfig",
        description: "The utxo ensuring proper configuration update"
      ),),
      outputs: ((
        name: "Config",
        description: "The utxo containing the updated config"
      ),(
        name: "Miner",
        description: "Miner fee"
      )),
      conditions: (
        "The tokens in the utxo are passed along to the output",
        "The value in the utxo is passed along to the output",
        "The output has the correct (new) contract",
        "There is a valid action in the put with action token and whitelisted contract"
      )
    ),
  ),
)

#render(contractDef)