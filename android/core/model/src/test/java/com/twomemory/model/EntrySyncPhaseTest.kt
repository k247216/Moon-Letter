package com.twomemory.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The queue is the only evidence a device has that a record reached the space, so
 * these classifications decide what the couple is told. A parked rejection must
 * outrank everything else: without it a rejected record looks delivered here while
 * the other phone never receives it.
 */
class EntrySyncPhaseTest {

    private fun row(state: String, attempts: Int = 0) = QueuedOperation(state, attempts)

    @Test
    fun anEmptyQueueIsTheOnlyThingAllowedToSayDelivered() {
        assertEquals(EntrySyncPhase.DELIVERED, entrySyncPhase(emptyList()))
    }

    @Test
    fun aParkedRejectionOutranksEveryOtherRow() {
        assertEquals(
            EntrySyncPhase.REJECTED,
            entrySyncPhase(listOf(row("PENDING", attempts = 3), row("MEDIA_PENDING"), row("CONFLICT"))),
        )
    }

    @Test
    fun aRecordWaitingOnItsPictureIsReportedAsUploading() {
        assertEquals(EntrySyncPhase.UPLOADING_MEDIA, entrySyncPhase(listOf(row("MEDIA_PENDING"), row("PENDING"))))
    }

    @Test
    fun aFailedSendStillInBackoffIsReportedAsRetrying() {
        assertEquals(EntrySyncPhase.RETRYING, entrySyncPhase(listOf(row("PENDING"), row("PENDING", attempts = 2))))
    }

    @Test
    fun aFirstTryIsReportedAsQueued() {
        assertEquals(EntrySyncPhase.QUEUED, entrySyncPhase(listOf(row("PENDING"), row("PENDING"))))
    }
}
