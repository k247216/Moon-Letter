package com.twomemory.editor

import java.io.File
import java.util.UUID

/** Owns only files created by this recording session, never existing attachments. */
class RecordingFiles(private val directory: File) {
    private val pending = mutableSetOf<String>()

    fun createPending(): File {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建录音目录" }
        return File(directory, "${UUID.randomUUID()}.m4a").also {
            check(it.createNewFile()) { "无法创建录音文件" }
            pending += it.absolutePath
        }
    }

    /** durationMs must come from successfully decoded audio, not a UI timer. */
    fun accept(file: File, durationMs: Long): File? {
        if (file.absolutePath !in pending || !file.isFile || file.length() < 128 || durationMs < 1_000) return null
        pending.remove(file.absolutePath)
        return file
    }

    fun discard(file: File) {
        if (pending.remove(file.absolutePath)) file.delete()
    }

    fun discardPending() {
        pending.toList().forEach { discard(File(it)) }
    }
}
