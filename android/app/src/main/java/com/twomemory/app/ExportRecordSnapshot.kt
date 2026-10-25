package com.twomemory.app

import com.twomemory.database.EntryEntity
import com.twomemory.database.EntryBlockEntity
import com.twomemory.database.CommentEntity

/** Immutable local rows captured before any network download or ZIP write. */
internal data class ExportRecordSnapshot(
    val entry: EntryEntity,
    val blocks: List<EntryBlockEntity>,
    val comments: List<CommentEntity>,
)
