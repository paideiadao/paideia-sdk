package im.paideia.common

object CompareTypes extends Enumeration {
        type CompareType = Value

        val LT, GT, EQ = Value

        case class FC[T](
            ct: CompareType,
            cv: T
        ) {
            case class BoxFilter(
                ergoTree: FC[Array[Byte]]
            )
        }
}

