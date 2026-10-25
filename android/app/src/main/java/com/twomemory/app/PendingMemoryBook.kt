package com.twomemory.app

import android.content.Context
import java.io.File

internal fun pendingBookFile(context: Context, name: String?): File? {
    if (name == null || !name.matches(Regex("MoonLetter_[0-9a-fA-F-]{36}\\.zip"))) return null
    return File(File(context.cacheDir, "memory-books"), name).takeIf { it.isFile && it.length() > 0 }
}
