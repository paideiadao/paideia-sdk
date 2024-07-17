def daoOriginKey(b: Box): Coll[Byte] = {
    b.R4[Coll[Byte]].get
}

def daoProposalToken(b: Box): (Coll[Byte], Long) = b.tokens(1)

def daoToken(b: Box): (Coll[Byte], Long) = b.tokens(0)

def daoActionToken(b: Box): (Coll[Byte], Long) = b.tokens(2)