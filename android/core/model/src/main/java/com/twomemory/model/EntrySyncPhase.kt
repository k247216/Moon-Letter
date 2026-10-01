package com.twomemory.model

/**
 * What this device still owes the server for one record, read from its own
 * outbox rows. A record can only be called delivered once nothing is left here:
 * the server accepts an operation by deleting it from the queue.
 */
enum class EntrySyncPhase {
    /** Nothing queued: the server took every operation for this record. */
    DELIVERED,

    /** Queued and not sent yet, or waiting for its turn behind an earlier operation. */
    QUEUED,

    /** Held back because a picture in this record has no asset id yet. */
    UPLOADING_MEDIA,

    /** At least one send failed and the engine backs off before trying again. */
    RETRYING,

    /**
     * The server rejected an operation for this record, so it stays parked and is
     * never retried on its own. Without this state a rejected record looks exactly
     * like a delivered one on this phone while the other device never gets it.
     */
    REJECTED,
}

/** Projection of one outbox row: enough to classify the queue without Room types. */
data class QueuedOperation(val state: String, val attemptCount: Int)

/**
 * Classifies the queue for one record. Order matters: a parked rejection outranks
 * everything, because it needs the couple to act rather than wait.
 */
fun entrySyncPhase(rows: List<QueuedOperation>): EntrySyncPhase = when {
    rows.any { it.state == "CONFLICT" } -> EntrySyncPhase.REJECTED
    rows.any { it.state == "MEDIA_PENDING" } -> EntrySyncPhase.UPLOADING_MEDIA
    rows.any { it.state == "PENDING" && it.attemptCount > 0 } -> EntrySyncPhase.RETRYING
    rows.any { it.state == "PENDING" } -> EntrySyncPhase.QUEUED
    else -> EntrySyncPhase.DELIVERED
}
