#import "../../lib/docTemplate/1.0.0/docTemplate.typ": *

#let contractDef = (
  title: "StakeProfitShare",
  version: "latest",
  summary: "This contract is a companion contract to the main stake contract. It ensures profit is properly updated according to the shared profit.",
  parameters: (
    (name: "imPaideiaDaoKey", description: "Token ID of the dao config nft", type: "Coll[Byte]", default: ""),),
  registers: (),
  assets: (),
  transactions: (
    (
      name: "StakeCompound",
      description: "In this transaction 1 or more SplitProfit boxes are handled by splitting them between the treasury and the stake state",
      contextVars: (
        (index: "0",
        type: "Coll[Byte]",
        description: "Proof for retrieving current DAO Config values"),
      ),
      configs: ((
        name: "im.paideia.staking.state.tokenid",
        type: "Coll[Byte]",
        description: "Token ID of this DAO's stake state token"
      ),(
        name: "im.paideia.contracts.staking.profitshare",
        type: "PaideiaContractSignature",
        description: "Signature of the stake profitshare contract"
      ),),
      dataInputs: ((
        name: "DAO Config",
        description: "Config utxo of this DAO"
      ),),
      inputs: ((
        name: "StakeState",
        description: "The utxo containing the staking state"
      ),(
        name: "StakeCompound",
        description: "The utxo ensuring proper execution of calculating the compound"
      ),),
      outputs: ((
        name: "StakeState",
        description: "The utxo containing the updated staking state"
      ),(
        name: "StakeCompound",
        description: "A copy of the input utxo"
      ),(
        name: "Miner",
        description: "Miner fee"
      ),),
      conditions: (
        "DAO Config input is correct",
        "Stake state input is correct",
        "The rewards provided in the update operations match expectations",
        "Staked token amounts are correct in the output",
        "Snapshot is updated correctly by removing handled stakers",
        "New stake state AVLTree digest matches expectations",
        "Stake compound is copied into new utxo",
        "Prevent spam attack by ensuring a minimum amount of stakers is handled, or this is the last batch of stakers to be handled",
        "The current profit in the stake state is updated according to distributed profit",
        "Register values not relevant to this action in the stake state are unchanged"
      )
    ),
  ),
)

#render(contractDef)