def ergInBoxes(boxes: Coll[Box]): Long = {
    boxes.fold(0L, {
            (z: Long, b: Box) => 
            z + b.value
        })
}