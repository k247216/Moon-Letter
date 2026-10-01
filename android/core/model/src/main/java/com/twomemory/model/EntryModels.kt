package com.twomemory.model

import java.time.Instant
import java.util.UUID

enum class EntryMode { PERSONAL, COLLABORATIVE }

enum class EntryState { DRAFT, PUBLISHED, CAPSULE_LOCKED, ARCHIVED }

enum class BlockType { TEXT, IMAGE, VIDEO, AUDIO, MUSIC, LOCATION }

data class LocalEntryCommand(
    val coupleId: UUID,
    val authorId: UUID,
    val mode: EntryMode,
    val occurredAt: Instant,
    val occurredTimezone: String,
    val title: String? = null,
    val blocks: List<LocalBlockCommand> = emptyList(),
    val entryId: UUID = UUID.randomUUID(),
)

data class LocalBlockCommand(
    val id: UUID = UUID.randomUUID(),
    val type: BlockType,
    val orderKey: Long,
    val payload: String,
    val authorId: UUID,
    val assetId: UUID? = null,
)

data class TimelineItem(
    val id: UUID,
    val coupleId: UUID,
    val mode: EntryMode,
    val state: EntryState,
    val occurredAt: Instant,
    val occurredTimezone: String,
    val title: String?,
    /** Writer of the entry; the UI renders 我 vs the partner label by it. */
    val authorId: UUID? = null,
    /** Plain text preview of the first content block, for the timeline card. */
    val preview: String? = null,
)

data class PendingOperation(
    val operationId: UUID,
    val coupleId: UUID,
    val entityId: UUID,
    val action: String,
    val payload: String,
    val baseVersion: Long,
    val attemptCount: Int,
)

data class RemoteChange(
    val sequence: Long,
    val entityType: String,
    val entityId: UUID,
    val operation: String,
    val payload: String?,
)
