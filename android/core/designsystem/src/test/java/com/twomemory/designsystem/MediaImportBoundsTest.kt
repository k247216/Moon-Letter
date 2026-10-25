package com.twomemory.designsystem

import kotlin.test.*
import java.io.*

class MediaImportBoundsTest {
    @Test fun tooLargeInputIsRejectedBeforeItCanBeConfirmed() {
        val output=ByteArrayOutputStream()
        assertFailsWith<IOException> { copyMediaBounded(ByteArrayInputStream(ByteArray(20)),output,10) }
        assertTrue(output.size()<=10)
    }
    @Test fun bytesWithinLimitArePreserved() {
        val original=byteArrayOf(1,2,3)
        val output=ByteArrayOutputStream()
        assertEquals(3L,copyMediaBounded(ByteArrayInputStream(original),output,3))
        assertContentEquals(original,output.toByteArray())
    }
}
