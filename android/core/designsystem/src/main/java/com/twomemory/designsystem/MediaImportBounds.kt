package com.twomemory.designsystem

import java.io.*

internal fun copyMediaBounded(input: InputStream, output: OutputStream, maxBytes: Long): Long {
    require(maxBytes>0)
    val buffer=ByteArray(8192)
    var count=0L
    while(true) {
        val n=input.read(buffer); if(n<0) break
        count+=n
        if(count>maxBytes) throw IOException("Media exceeds import limit")
        output.write(buffer,0,n)
    }
    return count
}
