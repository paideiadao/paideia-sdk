package im.paideia.common.filtering

import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.InputBox
import special.collection.Coll

/** FilterLeaf is a subclass of FilterNode that implements the matchBox method to check if
  * a given input box matches the specified filter criteria.
  *
  * @param filterType
  *   The filter type used for comparison.
  * @param compareValue
  *   The value to compare against.
  * @param compareField
  *   The field to which the comparison value will be compared.
  * @param listIndex
  *   An Integer representing a specific index in a list when compareField is ASSET or
  *   REGISTER.
  * @tparam T
  *   The type of values being compared (must implement Ordering).
  */
class FilterLeaf[T: Ordering](
  filterType: FilterType.Value,
  compareValue: T,
  compareField: CompareField.Value,
  listIndex: Int
) extends FilterNode(filterType, List[FilterNode]()) {

  /** Overrides the matchBox method from its parent class. Returns true if the given box
    * matches the specified filter criteria, false otherwise.
    *
    * @param box
    *   The input box to check for a match.
    * @return
    *   Boolean that is true if the input box matches the specified filter, false
    *   otherwise.
    */
  override def matchBox(box: InputBox): Boolean = {
    val compareFieldValue = (compareField match {
      case CompareField.ERGO_TREE => box.getErgoTree().bytesHex
      case CompareField.ASSET =>
        if (box.getTokens().size > listIndex)
          box.getTokens().get(listIndex).getId().toString()
        else ""
      case CompareField.REGISTER =>
        if (box.getRegisters().size > listIndex)
          box.getRegisters().get(listIndex).getValue() match {
            case c: Coll[_] => c.toArray.toIterable
            case v: T       => v
          }
        else None
      case CompareField.VALUE => box.getValue()
    }).asInstanceOf[T]
    val res = filterType match {
      case FilterType.FTEQ => compareFieldValue == compareValue
      case FilterType.FTGT => gt(compareFieldValue, compareValue)
      case FilterType.FTLT => lt(compareFieldValue, compareValue)
    }
    res
  }

  /** Private method to compare two objects of type T with > operator.
    *
    * @param v1
    *   Object of type T to compare.
    * @param v2
    *   Object of type T to compare.
    * @param ev
    *   Required implicitly evidence that T implements Ordered.
    * @return
    *   Boolean that is true if v1 > v2, false otherwise.
    */
  private def gt(v1: T, v2: T)(implicit ev: T => Ordered[T]): Boolean = v1 > v2

  /** Private method to compare two objects of type T with < operator.
    *
    * @param v1
    *   Object of type T to compare.
    * @param v2
    *   Object of type T to compare.
    * @param ev
    *   Required implicitly evidence that T implements Ordered.
    * @return
    *   Boolean that is true if v1 < v2, false otherwise.
    */
  private def lt(v1: T, v2: T)(implicit ev: T => Ordered[T]): Boolean = v1 < v2

}
