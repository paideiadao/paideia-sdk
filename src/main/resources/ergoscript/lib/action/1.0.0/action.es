def aProposalIndex(b: Box): Long = {
    b.R4[Coll[Long]].get(0)
}

def aProposalOption(b: Box): Long = {
    b.R4[Coll[Long]].get(1)
}

def aRepeats(b: Box): Long = {
    b.R4[Coll[Long]].get(2)
}

def aActivationTime(b: Box): Long = {
    b.R4[Coll[Long]].get(3)
}

def aRepeatTime(b: Box): Long = {
    b.R4[Coll[Long]].get(4)
}

def aActionToken(b: Box): (Coll[Byte], Long) = b.tokens(0)