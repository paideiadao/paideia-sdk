#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "Treasury",
  version: "latest",
  summary: "The DAO treasury. The assets guarded by this contract can only be spend through passed proposals or to fund the running of the DAO.",
  parameters: (
    (name: "daoKeyId", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "paideiaDaoKey", description: "Token ID of the paideia dao key", type: "Coll[Byte]", default: ""),
    (name: "paideiaTokenId", description: "Token ID of Paideia token", type: "Coll[Byte]", default: ""),
    (name: "daoActionTokenIdAndStakeStateTokenId", description: "Token ID of dao action token and stake state nft concatenated", type: "Coll[Byte]", default: ""),),
  registers: (),
  assets: (),
  transactions: (
    (
      name: "Treasury Spend",
      description: "In this transaction an action belonging to a passed proposal is used to spend from the treasury",
      contextVars: (
        (index: "0",
        type: "Byte",
        description: "Transaction type"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for DAO config values"),
      ),
      configs: ((
        name: "im.paideia.contracts.action.{action contract ergo tree bytes}",
        type: "PaideiaContractSignature",
        description: "Signature of the action contract"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),),
      inputs: ((
        name: "Treasury",
        description: "The utxo(s) protecting the treasury"
      ),(
        name: "SendFundsAction",
        description: "The utxo ensuring correct treasury spending"
      )),
      outputs: ((
        name: "Treasury",
        description: "Change utxo to treasury"
      ),(
        name: "Target",
        description: "The target utxo(s) as defined in the action"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),),
      conditions: (
        "DAO Config input is correct",
        "Action input is valid",
      )
    ),
    (
      name: "Stake snapshot",
      description: "A stake snapshot is paid for by the treasury, it includes a paideia fee to the paideia dao",
      contextVars: (
        (index: "0",
        type: "Byte",
        description: "Transaction type"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for Paideia DAO config values"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for DAO config values"),
      ),
      configs: ((
        name: "im.paideia.contracts.staking.compound",
        type: "PaideiaContractSignature",
        description: "Signature of the StakeCompound contract"
      ),(
        name: "im.paideia.contracts.staking.snapshot",
        type: "PaideiaContractSignature",
        description: "Signature of the StakeSnapshot contract"
      ),(
        name: "im.paideia.staking.emission",
        type: "Long",
        description: "Staking emission amount"
      ),(
        name: "im.paideia.dao.governance.tokenid",
        type: "Coll[Byte]",
        description: "Token ID of the governance token for this dao"
      ),(
        name: "im.paideia.fees.emit.paideia",
        type: "Long",
        description: "Fee in paideia to be paid to Paideia DAO"
      ),(
        name: "im.paideia.fees.emit.operator.paideia",
        type: "Long",
        description: "Fee in paideia to be paid to off chain operator"
      ),(
        name: "im.paideia.contracts.splitprofit",
        type: "PaideiaContractSignature",
        description: "Split profit contract for paideia dao"
      ),(
        name: "im.paideia.fees.operator.max.erg",
        type: "Long",
        description: "Max fee in nanoerg to be paid to off chain operator"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),(
        name: "Paideia DAO Config",
        description: "Config utxo of the Paideia DAO"
      ),),
      inputs: ((
        name: "Treasury",
        description: "The utxo(s) protecting the treasury"
      ),(
        name: "Stake State",
        description: "The stake state that will be snapshot"
      ),(
        name: "StakeSnapshot",
        description: "The companion contract to the stake state"
      )),
      outputs: ((
        name: "Treasury",
        description: "Change utxo to treasury"
      ),(
        name: "StakeState",
        description: "The updated stake state"
      ),(
        name: "StakeSnapshot",
        description: "The companion contract to the stake state"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),),
      conditions: (
        "DAO Config input is correct",
        "Paideia dao config is correct",
        "Stake state is correct",
        "Fee is correct",
        "no other assets leave the treasury"
      )
    ),
    (
      name: "Stake compound",
      description: "A stake compound is paid for by the treasury",
      contextVars: (
        (index: "0",
        type: "Byte",
        description: "Transaction type"),
        (index: "1",
        type: "Coll[Byte]",
        description: "Proof for Paideia DAO config values"),
        (index: "2",
        type: "Coll[Byte]",
        description: "Proof for DAO config values"),
      ),
      configs: ((
        name: "im.paideia.contracts.staking.compound",
        type: "PaideiaContractSignature",
        description: "Signature of the StakeCompound contract"
      ),(
        name: "im.paideia.contracts.staking.snapshot",
        type: "PaideiaContractSignature",
        description: "Signature of the StakeSnapshot contract"
      ),(
        name: "im.paideia.staking.emission",
        type: "Long",
        description: "Staking emission amount"
      ),(
        name: "im.paideia.dao.governance.tokenid",
        type: "Coll[Byte]",
        description: "Token ID of the governance token for this dao"
      ),(
        name: "im.paideia.fees.compound.operator.paideia",
        type: "Long",
        description: "Fee in paideia to be paid to off chain operator"
      ),(
        name: "im.paideia.fees.operator.max.erg",
        type: "Long",
        description: "Max fee in nanoerg to be paid to off chain operator"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),(
        name: "Paideia DAO Config",
        description: "Config utxo of the Paideia DAO"
      ),),
      inputs: ((
        name: "Treasury",
        description: "The utxo(s) protecting the treasury"
      ),(
        name: "Stake State",
        description: "The stake state that will be snapshot"
      ),(
        name: "StakeCompound",
        description: "The companion contract to the stake state"
      )),
      outputs: ((
        name: "Treasury",
        description: "Change utxo to treasury"
      ),(
        name: "StakeState",
        description: "The updated stake state"
      ),(
        name: "StakeCompound",
        description: "The companion contract to the stake state"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),),
      conditions: (
        "DAO Config input is correct",
        "Paideia dao config is correct",
        "Stake state is correct",
        "Fee is correct",
        "no other assets leave the treasury"
      )
    ),
  ),
)

#render(contractDef)