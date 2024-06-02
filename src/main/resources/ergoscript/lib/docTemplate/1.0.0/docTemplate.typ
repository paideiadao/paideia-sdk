#let render(contract) = [
  #set par(justify: true)
  #let t = {
    set text(size: 34pt)
    [#contract.title - #contract.version]
  }
  #t

  = Summary

  #contract.summary

  = Parameters

  #let table_content = for r in contract.parameters {
    (r.name, r.description, r.type, r.default)
  }
    #table(
    columns: 4,
    [*Parameter*], [*Description*],[*Type*],[*Default*],
    ..table_content
  )

  = Registers

  #let table_content = for (index, r) in contract.registers {
    (index, r.description, r.type)
  }
    #table(
    columns: 3,
    [*Register*], [*Description*],[*Type*],
    ..table_content
  )

  = Assets

  #let table_content = for r in contract.assets {
    (r.name, r.amount)
  }
    #table(
    columns: 2,
    [*Token name*], [*amount*],
    ..table_content
  )

  = Transactions

  #let table_content = for r in contract.transactions {
    (r.name, r.description)
  }
    #table(
    columns: 2,
    [*Transaction name*], [*Transaction description*],
    ..table_content
  )

  #for r in contract.transactions {
    [
      == #r.name

      #r.description

      === Context Variables

      #let table_content = for c in r.contextVars {
        (c.index, c.type, c.description)
      }
        #table(
        columns: 3,
        [*CV Index*], [*Type*],[*Description*],
        ..table_content
      )

      === Config

      #let table_content = for c in r.configs {
        (c.name, c.type, c.description)
      }
        #table(
        columns: 3,
        [*Config key*], [*Type*],[*Description*],
        ..table_content
      )

      === Inputs

      #let table_content = for c in r.inputs {
        (c.name, c.description)
      }
        #table(
        columns: 2,
        [*Utxo type*], [*Description*],
        ..table_content
      )

      === Data Inputs

      #let table_content = for c in r.dataInputs {
        (c.name, c.description)
      }
        #table(
        columns: 2,
        [*Utxo type*], [*Description*],
        ..table_content
      )

      === Outputs

      #let table_content = for c in r.outputs {
        (c.name, c.description)
      }
        #table(
        columns: 2,
        [*Utxo type*], [*Description*],
        ..table_content
      )

      === Conditions

      #let conditionList = for c in r.conditions {
        [+ #c]
      }
      #conditionList
    ]
  }
]