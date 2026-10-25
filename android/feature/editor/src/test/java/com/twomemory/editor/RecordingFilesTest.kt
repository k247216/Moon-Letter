package com.twomemory.editor

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests the real on-disk ownership boundary; audio decoding is tested on Android. */
class RecordingFilesTest {
    private fun withFiles(test: (Any, File) -> Unit) {
        val directory = Files.createTempDirectory("moonletter-recording-test").toFile()
        try {
            val type = runCatching { Class.forName("com.twomemory.editor.RecordingFiles") }.getOrNull()
            assertNotNull(type, "Recording file ownership policy must exist before a recorder can persist audio")
            test(type.getConstructor(File::class.java).newInstance(directory), directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun pending(files: Any): File = files.javaClass.getMethod("createPending").invoke(files) as File
    private fun accept(files: Any, file: File, durationMs: Long): File? = files.javaClass
        .getMethod("accept", File::class.java, java.lang.Long.TYPE).invoke(files, file, durationMs) as File?
    private fun discard(files: Any, file: File) {
        files.javaClass.getMethod("discard", File::class.java).invoke(files, file)
    }

    @Test fun emptyRecordingCannotBeConfirmed() = withFiles { files, _ ->
        val file = pending(files)
        assertNull(accept(files, file, 2_000L))
    }

    @Test fun shortRecordingCannotBeConfirmedEvenWithBytes() = withFiles { files, _ ->
        val file = pending(files).apply { writeBytes(ByteArray(512)) }
        assertNull(accept(files, file, 100L))
    }

    @Test fun missingRecordingCannotBeConfirmed() = withFiles { files, _ ->
        val file = pending(files).apply { delete() }
        assertNull(accept(files, file, 2_000L))
    }

    @Test fun cancellingDeletesOnlyTheSessionsPendingFile() = withFiles { files, directory ->
        val other = File(directory, "previous-recording.m4a").apply { writeText("stored reference") }
        val file = pending(files).apply { writeBytes(ByteArray(512)) }
        discard(files, file)
        assertFalse(file.exists())
        assertEquals("stored reference", other.readText())
    }

    @Test fun acceptedRecordingSurvivesSessionDisposal() = withFiles { files, _ ->
        // Duration comes from successful Android decoding in the real recorder.
        val file = pending(files).apply { writeBytes(ByteArray(512)) }
        val accepted = assertNotNull(accept(files, file, 2_000L))
        assertTrue(accepted.isFile)
        discard(files, file)
        assertTrue(accepted.isFile, "A confirmed attachment must no longer be owned by cancellation")
    }

    @Test fun unrelatedStoredFileCannotBeAcceptedOrDeleted() = withFiles { files, directory ->
        val stored = File(directory, "stored.m4a").apply { writeBytes(ByteArray(512)) }
        assertNull(accept(files, stored, 2_000L))
        discard(files, stored)
        assertTrue(stored.isFile)
    }

    @Test fun disposalDeletesAllPendingButKeepsAcceptedOriginal() = withFiles { files, _ ->
        val confirmed = pending(files).apply { writeBytes(ByteArray(512)) }
        assertNotNull(accept(files, confirmed, 2_000L))
        val abandoned = pending(files).apply { writeBytes(ByteArray(512)) }
        files.javaClass.getMethod("discardPending").invoke(files)
        assertFalse(abandoned.exists())
        assertTrue(confirmed.isFile)
    }
}
