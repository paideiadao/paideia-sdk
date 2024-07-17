#import "template.typ": *

#let HLR = (
  "1": (
    title: [Bootstrapping a DAO should be open to anyone],
    description: [Bootstrapping a DAO should be easy for the user and not require indepth technical knowledge.],
  ),
  "2": (
    title: [DAO should be highly configurable],
    description: [Many different factors can weigh in on how a DAO desires to operate, so things such as profit sharing, quorum and minimum proposal duration should be configurable by each individual DAO],
  ),
  "3": (
    title: [DAO members can vote on multiple proposals in parallel],
    description: [It is important to ensure a DAO member does not vote twice on the same proposal, it should be possible to vote on multiple proposals that are active at the same time.],
  ),
  "4": (
    title: [DAO's should be able to provide a staking setup],
    description: [A DAO should be able to reward it's members through a configurable staking setup],
  ),
  "5": (
    title: [DAO's should be able to share profit with it's members],
    description: [A typical usecase for a DAO besides governance is profit sharing. This should be possible to achieve without human interaction.],
  ),
  "6": (
    title: [Treasury spending],
    description: [The DAO should be able to spend from a treasury through a proposal that is voted on by it's members],
  ),
  "7": (
    title: [Updateable DAO config],
    description: [A DAO should be able to reconfigure itself through proposals voted on by it's members]
  )
)

#let actors = (
  "paideia_bootstrapper": (
    title: "Paideia Bootstrapper",
    description: "Wallet that initiates the Paideia protocol",
    interaction: ((
      index: "paideia_origin",
      transaction: "Bootstrap"
    ),
    )
  ),
  "paideia_origin": (
    title: "Paideia Origin",
    description: "Ensures that newly created DAO's are correct and supply them with token verifying this",
    interaction: ((
      index: "proto_dao",
      transaction: "Create Proto DAO"
      ),)
  ),
  "dao_creator": (
    title: "DAO Creator",
    description: "User initiating DAO creation process",
    interaction: ((
      index: "proto_dao_proxy",
      transaction: "Create proto dao proxy"
    ),)
  ),
  "proto_dao_proxy": (
    title: "Proto DAO Proxy",
    description: "Contains the assets needed to create a DAO and the desired intial configuration",
    interaction: ((
      index: "proto_dao",
      transaction: "Create Proto DAO"
    ),)
  ),
  "proto_dao": (
    title: "Proto DAO",
    description: "Ensures the correct tokens are minted",
    interaction: ((
      index: "mint",
      transaction: "Mint token",
    ),(
      index: "dao",
      transaction: "Create DAO"
    ),(
      index: "stake_state",
      transaction: "Create DAO"
    ),(
      index: "dao_config",
      transaction: "Create DAO"
    ))
  ),
  "mint": (
    title: "Mint",
    description: "Simple contract holding minted tokens until they can be deposited in their correct DAO contract",
    interaction: ((
      index: "dao",
      transaction: "Create DAO"
    ),(
      index: "stake_state",
      transaction: "Create DAO"
    ),(
      index: "dao_config",
      transaction: "Create DAO"
    ))
  ),
  "dao": (
    title: "DAO",
    description: "Holding proposal and action tokens and verifies new proposals are valid according to the dao configuration",
    interaction: ((
      index: "proposal",
      transaction: "Create Proposal"
    ),(
      index: "action",
      transaction: "Create Proposal"
    ))
  ),
  "stake_state": (
    title: "Stake State",
    description: "Holds the stake state and all staked tokens. Logic exceeds maximum script size so is broken up into sub contracts.",
    interaction: ()
  ),
  "dao_config": (
    title: "DAO Config",
    description: "Contains the dao configuration, usually used as a data input",
    interaction: ((
      index: "dao_config",
      transaction: "Update Config"
    ),)
  ),
  "dao_member": (
    title: "DAO Member",
    description: "(Potential) member of a DAO interacting with it",
    interaction: ((
      index: "stake_proxy",
      transaction: "Request stake"
    ),(
      index: "create_proposal",
      transaction: "Create Proposal"
    ),(
      index: "vote",
      transaction: "Cast Vote"
    ))
  ),
  "stake_proxy": (
    title: "Stake Proxy",
    description: "Avoids singleton contention and ensures the stake is created according to DAO members' wishes",
    interaction: ((
      index: "stake_state",
      transaction: "Stake"
    ),(
      index: "dao_member",
      transaction: "Stake"
    ))
  ),
  "create_proposal": (
    title: "Create Proposal",
    description: "Avoids contention on DAO utxo and ensures the proposal is created as the user intends it",
    interaction: ((
      index: "proposal",
      transaction: "Create Proposal"
    ),(
      index: "action",
      transaction: "Create Proposal"
    ))
  ),
  "action": (
    title: "Action",
    description: "A whitelisted action type, such as spend from treasury or update configuration",
    interaction: ((
      index: "dao_config",
      transaction: "Update Config"
    ),(
      index: "spending_target",
      transaction: "Treasury Spend"
    ))
  ),
  "proposal": (
    title: "Proposal",
    description: "A whitelisted proposal contract type, keeping track of votes and state (passed, failed, etc.)",
    interaction: ((
      index: "proposal",
      transaction: "Evaluate Proposal"
    ),)
  ),
  "treasury": (
    title: "Treasury",
    description: "Holds funds owned by the DAO. Spending is done through proposals and actions",
    interaction: ((
      index: "spending_target",
      transaction: "Treasury Spend"
    ),)
  ),
  "vote": (
    title: "Vote",
    description: "Avoids contention on proposal utxo and ensures the vote is cast according to the user's wishes",
    interaction: ((
      index: "proposal",
      transaction: "Vote"
    ),)
  )
)

#show_intro(title: "Paideia DAO contracts", body: "The contracts support fully on chain dao creation, profit sharing, proposal creation and treasury management.")

#show_hlr(hlr: HLR, body: "For Paideia we have the following High Level Requirements (HLR) that we would like the contracts to fulfill.")

#show_overview(actors: actors, body: "Test")
