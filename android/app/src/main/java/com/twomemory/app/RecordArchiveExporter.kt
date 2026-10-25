package com.twomemory.app

import android.content.Context
import com.twomemory.database.AppDatabase
import java.util.UUID

/** Machine-readable published records, unchanged originals and integrity manifest, no presentation. */
internal object RecordArchiveExporter {
    suspend fun build(context: Context, database: AppDatabase, ownId: UUID?, ownName: String, partnerName: String): MemoryBook =
        MemoryBookExporter.build(context, database, ownId, ownName, partnerName, archiveOnly = true)
}
