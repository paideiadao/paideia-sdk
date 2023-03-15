package im.paideia.common.filtering

/**
  * Enumeration for different types of filters used in filtering InputBoxes.
  */
object FilterType extends Enumeration {
  type FilterType = Value

  /** To check if a value equals the given compare value. */
  val FTEQ: FilterType = Value

  /** To check if a value is greater than the given compare value. */
  val FTGT: FilterType = Value

  /** To check if a value is less than the given compare value. */
  val FTLT: FilterType = Value

  /** To check if any child filter matches. */
  val FTANY: FilterType = Value

  /** To check if all child filters match. */
  val FTALL: FilterType = Value
}
