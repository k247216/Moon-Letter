package com.twomemory.editor

import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.model.BlockType
import com.twomemory.model.CityCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EditorMediaOwnershipTest {
    @Test fun detachingSavedMediaNeverDeletesTheOnlyPersistedCopy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("saved-media-", ".mp4", instrumentation.targetContext.cacheDir)
        try {
            val vm = EditorViewModel()
            instrumentation.runOnMainSync {
                vm.addMediaAttachment(BlockType.VIDEO, file.absolutePath, "video/mp4")
                vm.publish { }
            }
            assertTrue(vm.state.value.saved)
            instrumentation.runOnMainSync {
                vm.updateBody("保存后继续编辑")
                vm.removeAttachment(vm.state.value.attachments.single().id)
            }
            assertTrue("Detach must not delete media potentially owned by a persisted record", file.exists())
            assertTrue(vm.state.value.attachments.isEmpty())
        } finally { file.delete() }
    }

    @Test fun selectingTheSameCityTwiceDoesNotDuplicateItsStory() {
        val vm = EditorViewModel()
        vm.addCity(CityCatalog.find("杭州")!!)
        vm.addCity(CityCatalog.find("杭州")!!)
        assertEquals(1, vm.state.value.attachments.count { it.type == BlockType.LOCATION })
    }
}
