#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "SplitProfit",
  version: "latest",
  summary: "This contract will make sure any assets deposited in its address are split correctly between the stakers and the treasury",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),
    (name: "stakeStateTokenId", description: "Token ID of the stake state nft", type: "Coll[Byte]", default: "")),
  registers: (),
  assets: (),
  transactions: (
    (
      name: "Split profit",
      description: "In this transaction profit is split between the dao treasury and the stakers",
      contextVars: (
        (index: "0",
        type: "Coll[Byte]",
        description: "Proof for retrieving dao config"),
      ),
      configs: ((
        name: "im.paideia.contracts.treasury",
        type: "PaideiaContractSignature",
        description: "Signature of treasury contract"
      ),(
        name: "im.paideia.contracts.staking.state",
        type: "PaideiaContractSignature",
        description: "Signature of stake state contract"
      ),(
        name: "im.paideia.staking.profit.sharepct",
        type: "Byte",
        description: "Pct of profit shared with stakers"
      ),(
        name: "im.paideia.dao.governance.tokenid",
        type: "Coll[Byte]",
        description: "Token ID of governance token for this dao"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config for this dao"
      ),),
      inputs: ((
        name: "Split profit",
        description: "One or more Split profit utxos"
      ),(
        name: "Stake State",
        description: "Stake state utxo"
      ),(
        name: "Stake profitshare",
        description: "Profitshare logic"
      ),),
      outputs: ((
        name: "Stake State",
        description: "Stake state utxo"
      ),(
        name: "Stake profitshare",
        description: "Profitshare logic"
      ),(
        name: "Treasury",
        description: "Treasury of the DAO"
      ),(
        name: "Miner",
        description: "Miner fee"
      )),
      conditions: (
        "Stake state is correct",
        "Treasury is correct",
        "No tokens burned",
        "No other outputs",
      )
    ),
  ),
)

#render(contractDef)