package im.paideia.common.filtering

/**
  *  Enumeration of fields used for comparing the equality between InputBoxes.
  *  These fields are the ErgoTree, value, assets and registers.
  */
object CompareField extends Enumeration {
  type CompareField = Value

  /** field to compare the ErgoTree script bytes */
  val ERGO_TREE: CompareField.Value = Value

  /** field to compare the Box value (in nanoERG) */
  val VALUE: CompareField.Value = Value

  /** field to compare the asset of a box */
  val ASSET: CompareField.Value = Value

  /** field to compare the register values of a box */
  val REGISTER: CompareField.Value = Value
}
