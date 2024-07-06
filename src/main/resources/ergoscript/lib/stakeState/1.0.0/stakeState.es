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

def snapshotStaked(b: Box): Coll[Long] = {
    b.R6[Coll[Coll[Long]]].get(0)
}

def snapshotVoted(b: Box): Coll[Long] = {
    b.R6[Coll[Coll[Long]]].get(1)
}

def snapshotVotesCast(b: Box): Coll[Long] = {
    b.R6[Coll[Coll[Long]]].get(2)
}

def snapshotPureParticipationWeight(b: Box): Coll[Long] = {
    b.R6[Coll[Coll[Long]]].get(3)
}

def snapshotParticipationWeight(b: Box): Coll[Long] = {
    b.R6[Coll[Coll[Long]]].get(4)
}

def snapshotTrees(b: Box): Coll[(AvlTree,AvlTree)] = {
    b.R7[Coll[(AvlTree,AvlTree)]].get
}

def snapshotProfit(b: Box): Coll[Long] = {
    b.R8[Coll[Long]].get
}

def stakeStateNFT(box: Box): Coll[Byte] = {
    box.tokens(0)._1
}

def stakeStateToken(b: Box): (Coll[Byte], Long) = b.tokens(0)

def govToken(b: Box): (Coll[Byte], Long) = b.tokens(1)

def otherTokens(b: Box): Coll[(Coll[Byte], Long)] = b.tokens.slice(2,b.tokens.size)