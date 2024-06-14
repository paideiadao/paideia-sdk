def stakeTree(b: Box): AvlTree = {
    b.R4[Coll[AvlTree]].get(0)
}

def participationTree(b: Box): AvlTree = {
    b.R4[Coll[AvlTree]].get(1)
}

def nextEmission(b: Box): Long = {
    b.R5[Coll[Long]].get(0)
}

def totalStaked(b: Box): Long = {
    b.R5[Coll[Long]].get(1)
}

def stakers(b: Box): Long = {
    b.R5[Coll[Long]].get(2)
}

def votedThisCycle(b: Box): Long = {
    b.R5[Coll[Long]].get(3)
}

def votesCastThisCycle(b: Box): Long = {
    b.R5[Coll[Long]].get(4)
}

def profit(b: Box): Coll[Long] = {
    b.R5[Coll[Long]].get.slice(5,b.R5[Coll[Long]].get.size)
}

def snapshotValues(b: Box): Coll[Coll[Long]] = {
    b.R6[Coll[Coll[Long]]].get
}

def snapshotTrees(b: Box): Coll[(AvlTree,AvlTree)] = {
    b.R7[Coll[(AvlTree,AvlTree)]].get
}

def snapshotProfit(b: Box): Coll[Long] = {
    b.R8[Coll[Long]].get
}