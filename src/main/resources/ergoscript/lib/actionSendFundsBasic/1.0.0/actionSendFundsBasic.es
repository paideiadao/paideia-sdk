#import lib/action/1.0.0/action.es;

def sfaOutputs(b: Box): Coll[Box] = {
    b.R5[Coll[Box]].get
}