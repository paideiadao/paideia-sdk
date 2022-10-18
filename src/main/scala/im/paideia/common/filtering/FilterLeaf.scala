package im.paideia.common.filtering

import org.ergoplatform.appkit.InputBox

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
            case CompareField.REGISTER => box.getRegisters().get(listIndex)
            case CompareField.VALUE => box.getValue()
        }).asInstanceOf[T]
        filterType match {
            case FilterType.FTEQ => compareFieldValue == compareValue
            case FilterType.FTGT => gt(compareFieldValue,compareValue)
            case FilterType.FTLT => lt(compareFieldValue,compareValue)
        }
    }

    def gt(v1: T, v2: T)(implicit ev: T => Ordered[T]): Boolean = v1 > v2
    def lt(v1: T, v2: T)(implicit ev: T => Ordered[T]): Boolean = v1 < v2
}
