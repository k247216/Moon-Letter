package com.twomemory.app

import com.twomemory.database.EntryEntity
import com.twomemory.database.EntryBlockEntity
import com.twomemory.model.TimelineItem

internal fun projectTimelineRows(entries: List<EntryEntity>,blocks: List<EntryBlockEntity>): List<TimelineItem> {
    val byEntry=blocks.groupBy {it.entryId}
    return entries.map {entity ->
        val own=byEntry[entity.id].orEmpty()
        entity.toTimelineItem(previewOf(own),photoOf(own),attachmentsOf(own),notePaperOf(own))
    }
}
