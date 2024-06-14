def pIndex(b: Box): Int = {
    b.R4[Coll[Int]].get(0)
}

def pPassedOption(b: Box): Int = {
    b.R4[Coll[Int]].get(1)
}