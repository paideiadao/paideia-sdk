#import lib/action/1.0.0/action.es;

def ucaDeletes(b: Box): Coll[Coll[Byte]] = {
    b.R5[Coll[Coll[Byte]]].get
}

def ucaUpdates(b: Box): Coll[(Coll[Byte], Coll[Byte])] = {
    b.R6[Coll[(Coll[Byte], Coll[Byte])]].get
}

def ucaInserts(b: Box): Coll[(Coll[Byte], Coll[Byte])] = {
    b.R7[Coll[(Coll[Byte], Coll[Byte])]].get
}