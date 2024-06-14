def pIndex(b: Box): Int = {
    b.R4[Coll[Int]].get(0)
}

def pPassedOption(b: Box): Int = {
    b.R4[Coll[Int]].get(1)
}

def pEndTime(b: Box): Long = {
    b.R5[Coll[Long]].get(0)
}

def pVoted(b: Box): Long = {
    b.R5[Coll[Long]].get(1)
}

def pVotes(b: Box): Coll[Long] = {
    b.R5[Coll[Long]].get.slice(2,b.R5[Coll[Long]].get.size)
}

def pVoteTree(b: Box): AvlTree = {
    b.R6[AvlTree].get
}