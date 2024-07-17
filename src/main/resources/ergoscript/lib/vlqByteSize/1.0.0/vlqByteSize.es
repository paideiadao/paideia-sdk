def vlqByteSize(l: Long): Int = {
        if(l < 128L) 1
        else if(l < 16384L) 2
        else if(l < 2097152L) 3
        else if(l < 268435456L) 4
        else if(l < 34359738368L) 5
        else if(l < 4398046511104L) 6
        else if(l < 562949953421312L) 7
        else if(l < 72057594037927936L) 8
        else 9
    }