package im.paideia.common.filtering

import org.ergoplatform.appkit.InputBox

class FilterNode(
    filterType: FilterType.Value,
    children: List[FilterNode]
) {
    def matchBox(box: InputBox): Boolean = {
        val childrenMatches = children.map((child: FilterNode) => child.matchBox(box))
        filterType match {
            case FilterType.FTALL => childrenMatches.forall(identity)
            case FilterType.FTANY => childrenMatches.exists(identity)
        }
    }
}