package im.paideia.common.filtering

import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.ErgoValue
import special.collection.Coll

class FilterLeaf[T: Ordering](
    filterType: FilterType.Value,
    compareValue: T,
    compareField: CompareField.Value,
    listIndex: Int
) extends FilterNode(filterType,List[FilterNode]()) {
    override def matchBox(box: InputBox): Boolean = {
        val compareFieldValue = (compareField match {
            case CompareField.ERGO_TREE => box.getErgoTree().bytesHex
            case CompareField.ASSET => if (box.getTokens().size > listIndex) box.getTokens().get(listIndex).getId().toString() else ""
            case CompareField.REGISTER => if (box.getRegisters().size > listIndex) box.getRegisters().get(listIndex).getValue() match {
                case c: Coll[_] => c.toArray.toIterable
                case v: T => v
            } else None
            case CompareField.VALUE => box.getValue()
        }).asInstanceOf[T]
        val res = filterType match {
            case FilterType.FTEQ => compareFieldValue == compareValue
            case FilterType.FTGT => gt(compareFieldValue,compareValue)
            case FilterType.FTLT => lt(compareFieldValue,compareValue)
        }
        res
    }

    def gt(v1: T, v2: T)(implicit ev: T => Ordered[T]): Boolean = v1 > v2
    def lt(v1: T, v2: T)(implicit ev: T => Ordered[T]): Boolean = v1 < v2
}
