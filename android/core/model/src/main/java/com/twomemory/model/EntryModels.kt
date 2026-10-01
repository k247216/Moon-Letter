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
    /** Where this entry's first picture lives, when it has one. */
    val photo: TimelinePhoto? = null,
)

/** A picture address: the copy this phone kept, or the one the server holds. */
data class TimelinePhoto(val localPath: String?, val assetId: String?)

data class EntryBlock(
    val id: UUID,
    val type: BlockType,
    val orderKey: Long,
    /** Text of a TEXT block; already unwrapped from its stored payload. */
    val text: String? = null,
    /** Picture this device kept a copy of, when there is one. */
    val localPath: String? = null,
    /** Server asset to fetch when no local copy exists. */
    val assetId: String? = null,
)

data class EntryComment(
    val id: UUID,
    val entryId: UUID,
    val authorId: UUID?,
    val body: String,
    val createdAt: Instant,
)

data class EntryDetail(
    val entry: TimelineItem,
    val blocks: List<EntryBlock>,
    val comments: List<EntryComment>,
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
