# paideia-sdk

This SDK contains the contracts and immediate surrounding off chain code for running Paideia DAO's.

## Bug Bounty

To help ensure the contracts validity and security we have a bug bounty program running up till launch (and potentially afterwards as well). The contracts are located in src/main/resources/ergoscript. Only version 1.0.0 contracts are in scope for this bug bounty. Before reviewing the contracts read about the configuration setup described further down in this document and the overview document in the docs directory.
If you find an issue create a github issue in this repository describing the issue as detailed as possible. Based on potential negative effects of the issue it will be classified according to the below table. An issue will be rewarded with a certain amount of Paideia or if many issues are found a share of the total bug bounty pool based on the points an issue is awarded.

| Issue type | Description | Max reward |
| --- | --- | --- |
| Critical | An issue that can result in significant loss of assets, either by malicious actions or protocol locking. | 10000 Pai |
| Major | An issue that causes the protocol to not function as intended resulting in unexpected results (for example a proposal being evaluated wrongly) | 5000 Pai |
| Minor | A smaller issue causing the protocol to behave wrong in edge cases | 1000 Pai |
| Informational | Lacking documentation, confusing code etc. | 500 Pai |

## Config value serialization
To allow for maximum flexibility in the config values stored for a dao and virtually no upper limit to the amount of them they are serialized into byte arrays and stored in an avl tree.
The off chain code handles the serialization and deserialization mostly automatically, giving you the original values to work with. Sadly this is not possible in ergoscript so to use the values inside a contract knowledge is needed on how the values are serialized.

### General rule
A serialized config value will start with one byte denoting the data type (see below for a table). If the data type is static length the following bytes will be the actual value, if it is variable length, the size will be defined by the first 4 bytes (32 bit int) following the datatype byte.

### Data types
| JVM data type | Byte value | Size bytes | Serialization method |
| --- | --- | --- | --- |
| Byte | 0 | 0 | 1:1 |
| Short | 1 | 0 | com.google.common.primitives.Shorts |
| Int | 2 | 0 | com.google.common.primitives.Ints |
| Long | 3 | 0 | com.google.common.primitives.Longs |
| BigInt | 4 | 0 | BigInt.toByteArray |
| Boolean | 5 | 0 | True = 1 False = 0
| String | 6 | 4 | String.getBytes(StandardCharsets.UTF_8) |
| PaideiaContractSignature | 7 | 0 | 32 bytes blake2b256 hash of contract ++ String serialized class name ++ String serialized version ++ networkPrefix byte |
| Array | 10 | 4* | Inner type byte ++ size bytes ++ data |
| Tuple | 20 | 0 | Serialized first entry ++ serialized second entry |

\* Note that the size bytes for an array are prepended with the inner data type

## DAO Configs

A dao can have many configurations stored (stored in an AVLtree so the "forest" is the limit). Here we list the ones used by default in the Paideia protocol, but it is possible to add your own.
Some are specific to the Paideia DAO itself and will not be relevant to other DAO's other than that they might influence fees etc. Some configurations only affect web 2, such as dao description on the site and the logo.

### DAO Configs - All DAO's

| Key | Data type | Web 2 | Description | Default | Min | Max |
| --- | --- | --- | --- | --- | --- | --- |
| im.paideia.contracts.dao | PaideiaContractSignature | x | DAO Origin contract handles proposal creation | | | |
| im.paideia.contracts.config | PaideiaContractSignature | x | Config contract holds the DAO configuration in an AVL tree | | | |
| im.paideia.contracts.treasury | PaideiaContractSignature | x | Treasury contract protects DAO treasury | | | |
| im.paideia.contracts.staking.state | PaideiaContractSignature | x | StakeState contract guards the staking state of the DAO | | | |
| im.paideia.contracts.staking.stake | PaideiaContractSignature | x | Stake contract handles creation of a new stake (in effect creation of a new DAO member) | | | |
| im.paideia.contracts.staking.changestake | PaideiaContractSignature | x | ChangeStake contract handles changing of a stake (adding or partially removing stake) | | | |
| im.paideia.contracts.staking.unstake | PaideiaContractSignature | x | Unstake contract handles fully removing a stake | | | |
| im.paideia.contracts.staking.snapshot | PaideiaContractSignature | x | StakeSnapshot handles snapshot creation of stake state | | | |
| im.paideia.contracts.staking.vote | PaideiaContractSignature | x | StakeVote handles governance acctivity registration when voting | | | |
| im.paideia.contracts.staking.compound | PaideiaContractSignature | x | StakeCompound contract handles dividing staking rewards and profit share based on relative stake size and governance participation | | | |
| im.paideia.contracts.staking.profitshare | PaideiaContractSignature | x | StakeProfitShare handles registration of shared profit that is to be distributed to stakers | | | |
| im.paideia.contracts.split.profit | PaideiaContractSignature | x | SplitProfit contract splits profit according to the DAO configuration between the treasury and the stakers | | | |
| im.paideia.dao.name | String | x | Name of the DAO | "" | | |
| im.paideia.dao.url | String | v | Url route to be used for accessing the DAO (https://app.paideia.im/{im.paideia.dao.url}) | Defaults to dao name | | |
| im.paideia.dao.desc | String | v | Description of the DAO on the website | "" | | |
| im.paideia.dao.theme | String | v | Theme to be used in this DAO | | | | 
| im.paideia.dao.logo | String | v | URL to logo of the DAO | | | |
| im.paideia.dao.banner | String | v | URL to banner image | | | |
| im.paideia.dao.banner.enabled | Boolean | v | Is banner enabled | | | |
| im.paideia.dao.footer | String | v | Text to be shown in footer of DAO | | | |
| im.paideia.dao.footer.enabled | Boolean | v | Is footer enabled | | | |
| im.paideia.dao.tokenid | Array[Byte] | x | Token ID of the governance token for this DAO | | | |
| im.paideia.dao.proposal.tokenid | Array[Byte] | x | Token ID of token proving valid proposal | | | |
| im.paideia.dao.action.tokenid | Array[Byte] | x | Token ID of token proving valid action | | | |
| im.paideia.dao.key | Array[Byte] | x | Token ID of NFT held by this DAO's config utxo. This is the unique identifier of the DAO | | | |
| im.paideia.dao.quorum | Long | x | Quorum (percentage of total possible votes) needed for a proposal to pass in promille (50% is 500), setting this too high could result in a soft locked DAO | 100L | 0L | 1000L | 
| im.paideia.dao.threshold | Long | x | Threshold (percentage of cast votes that need to be in favor) for a proposal to pass | 500L | 500L | 1000L |
| im.paideia.dao.min.proposal.time | Long | x | Minimum duration of a proposal in milliseconds (Min and default a day, maximum 3 months) | 86400000L | 86400000L | 7776000000L |
| im.paideia.dao.min.stake.proposal | Long | x | Minimum stake amount DAO member needs to have to be able to create a proposal | 0L | 0L | Total staked divided by half (prevents locking DAO if set to higher than total supply) |
| im.paideia.staking.state.tokenid | Array[Byte] | x | Token ID of the NFT identifying the utxo holding the stake state | | | |
| im.paideia.staking.emission.amount | Long | x | Amount of governance tokens to be distributed each staking epoch as rewards, taken from the treasury | 0L | 0L | 9999999999999999999L |
| im.paideia.staking.emission.delay | Long | x | Delay in epochs for a stake to start earning rewards, this can help incentivize keeping a stake (1L = no delay) | 1L | 1L | 10L | 
| im.paideia.staking.cyclelength | Long | x | Length of staking epoch in milliseconds | 86400000L | 86400000L | 999999999999L |
| im.paideia.staking.profit.sharepct | Long | x | Percentage of profit to be shared with stakers | 50L | 0L | 100L |
| im.paideia.staking.weight.pureparticipation | Byte | x | Percentage weight of pure participation in governance when calculating rewards distribution | 0 | 0 | 100 |
| im.paideia.staking.weight.participation | Byte | x | Percentage weight of participation in governance when calculating rewards distribution | 0 | 0 | 100 |
| im.paideia.contracts.proposal.{proposal contract ergotree bytes} | PaideiaContractSignature | x | Whitelisted proposal contract | | | |
| im.paideia.contracts.action.{action contract ergotree bytes} | PaideiaContractSignature | x | Whitelisted action contract | | | |

### DAO Configs - Paideia Only

| Key | Data type | Web 2 | Description | Default | Min | Max |
| --- | --- | --- | --- | --- | --- | --- |
| im.paideia.default.dao | Array[Byte] | x | The ergotree bytes for the default DAO Origin contract, a newly created DAO should follow this | | | | 
| im.paideia.default.dao.signature | PaideiaContractSignature | x | Signature of the default DAO Origin contract | | | |
| im.paideia.default.treasury | Array[Byte] | x | The ergotree bytes for the default Treasury contract, a newly created DAO should follow this | | | | 
| im.paideia.default.treasury.signature | PaideiaContractSignature | x | Signature of the default Treasury contract | | | |
| im.paideia.default.config | Array[Byte] | x | The ergotree bytes for the default Config contract, a newly created DAO should follow this | | | | 
| im.paideia.default.config.signature | PaideiaContractSignature | x | Signature of the default Config contract | | | |
| im.paideia.default.action.sendfunds | Array[Byte] | x | The ergotree bytes for the default ActionSendFunds contract, a newly created DAO should follow this | | | | 
| im.paideia.default.action.sendfunds.signature | PaideiaContractSignature | x | Signature of the default ActionSendFunds contract | | | |
| im.paideia.default.action.updateconfig | Array[Byte] | x | The ergotree bytes for the default ActionUpdateConfig contract, a newly created DAO should follow this | | | | 
| im.paideia.default.action.updateconfig.signature | PaideiaContractSignature | x | Signature of the default ActionUpdateConfig contract | | | |
| im.paideia.default.proposal.basic | Array[Byte] | x | The ergotree bytes for the default Proposal contract, a newly created DAO should follow this | | | | 
| im.paideia.default.proposal.basic.signature | PaideiaContractSignature | x | Signature of the default Proposal contract | | | |
| im.paideia.default.staking.change | Array[Byte] | x | The ergotree bytes for the default ChangeStake contract, a newly created DAO should follow this | | | | 
| im.paideia.default.staking.change.signature | PaideiaContractSignature | x | Signature of the default ChangeStake contract | | | |
| im.paideia.default.staking.stake | Array[Byte] | x | The ergotree bytes for the default Stake contract, a newly created DAO should follow this | | | | 
| im.paideia.default.staking.stake.signature | PaideiaContractSignature | x | Signature of the default Stake contract | | | |
| im.paideia.default.staking.compound | Array[Byte] | x | The ergotree bytes for the default StakeCompound contract, a newly created DAO should follow this | | | | 
| im.paideia.default.staking.compound.signature | PaideiaContractSignature | x | Signature of the default StakeCompound contract | | | |
| im.paideia.default.staking.profitshare | Array[Byte] | x | The ergotree bytes for the default StakeProfitShare contract, a newly created DAO should follow this | | | | 
| im.paideia.default.staking.profitshare.signature | PaideiaContractSignature | x | Signature of the default StakeProfitShare contract | | | |
| im.paideia.default.staking.snapshot | Array[Byte] | x | The ergotree bytes for the default StakeSnapshot contract, a newly created DAO should follow this | | | | 
| im.paideia.default.staking.snapshot.signature | PaideiaContractSignature | x | Signature of the default StakeSnapshot contract | | | |
| im.paideia.default.staking.state | Array[Byte] | x | The ergotree bytes for the default StakeState contract, a newly created DAO should follow this | | | | 
| im.paideia.default.staking.state.signature | PaideiaContractSignature | x | Signature of the default StakeState contract | | | |
| im.paideia.default.staking.vote | Array[Byte] | x | The ergotree bytes for the default StakeVote contract, a newly created DAO should follow this | | | | 
| im.paideia.default.staking.vote.signature | PaideiaContractSignature | x | Signature of the default StakeVote contract | | | |
| im.paideia.default.staking.unstake | Array[Byte] | x | The ergotree bytes for the default Unstake contract, a newly created DAO should follow this | | | | 
| im.paideia.default.staking.unstake.signature | PaideiaContractSignature | x | Signature of the default Unstake contract | | | |
| im.paideia.fees.createdao.erg | Long | x | The amount of nanoerg is taken as fee for creating a DAO, the fee is sent to the profit share contract | 0L | 0L | 999999999999L |
| im.paideia.fees.createdao.paideia | Long | x | The amount of Paideia is taken as fee for creating a DAO, the fee is sent to the profit share contract | 0L | 0L | 999999999999L |
| im.paideia.fees.createproposal.paideia | Long | x | The amount of Paideia is taken as fee for creating a proposal, the fee is sent to the profit share contract | 0L | 0L | 9999999999L |
| im.paideia.fees.emit.paideia | Long | x | The amount of Paideia is taken as fee per staking epoch per staker, the fee is sent to the profit share contract | 0L | 0L | 9999999999L |
| im.paideia.fees.emit.operator.paideia | Long | x | The amount of Paideia given to the operator performing the snapshot | 0L | 0L | 9999999999L |
| im.paideia.fees.operator.max.erg | Long | x | Maximum amount of nanoerg the operator can take when performing a snapshot | 0L | 0L | 9999999999L |
| im.paideia.fees.compound.operator.paideia | Long | x | The amount of Paideia the operator gets for performing a compound | 0L | 0L | 9999999999L |
| im.paideia.contracts.protodao | PaideiaContractSignature | x | The contract that handles the DAO creation process | | | |
| im.paideia.contracts.protodaoproxy | PaideiaContractSignature | x | The contract the user sends the initial DAO configuration to | | | |
| im.paideia.contracts.mint | PaideiaContractSignature | x | The Mint contract holds tokens minted during the DAO creation process | | | |
| im.paideia.contracts.createdao | PaideiaContractSignature | x | This is a companion contract to the proto dao contract | | | |