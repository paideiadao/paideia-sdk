package im.paideia.common.filtering

import org.ergoplatform.appkit.InputBox

/**
  * Class representing a filtering node.
  *
  * @param filterType The type of the filter to apply.
  * @param children The list of FilterNodes to evaluate.
  *
  */
class FilterNode(
  filterType: FilterType.Value,
  children: List[FilterNode]
) {

  /**
    * Checks if the provided InputBox matches this filter.
    *
    * @param box The input box to be checked
    * @return true if the given box matches this filter, false otherwise.
    */
  def matchBox(box: InputBox): Boolean = {
    val childrenMatches = children.map((child: FilterNode) => child.matchBox(box))
    filterType match {
      case FilterType.FTALL => childrenMatches.forall(identity)
      case FilterType.FTANY => childrenMatches.exists(identity)
    }
  }
}
