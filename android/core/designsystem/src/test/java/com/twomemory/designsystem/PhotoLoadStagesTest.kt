package com.twomemory.designsystem

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PhotoLoadStagesTest {
    @Test fun unsupportedPreviewDoesNotPreventOriginalLoading() = runBlocking {
        var originals=0
        val result=loadPhotoStages<String>(preview={throw java.io.IOException("415 unsupported preview")},
            original={originals++; "original"}, showPreview={fail("No preview available")})
        assertEquals("original",result); assertEquals(1,originals)
    }
}
