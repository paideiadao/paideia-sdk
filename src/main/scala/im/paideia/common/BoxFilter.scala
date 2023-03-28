package im.paideia.common

/**
  * Defines the ADT for CompareType with LT, GT, and EQ as members
  */
object CompareTypes extends Enumeration {
  type CompareType = Value

  val LT, GT, EQ = Value

  /**
    * Filter composition implementation
    * @tparam T The type of value being compared
    * @param ct The operation to evaluate the comparison
    * @param cv The value against which to compare
    */
  case class FC[T](ct: CompareType, cv: T) {

    /**
      * Represents criteria by which boxes should be filtered
      * @param ergoTree A criterion applied to the ErgoTree of a box
      */
    case class BoxFilter(ergoTree: FC[Array[Byte]])
  }
}
