package com.twomemory.app

import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.model.EntrySyncPhase
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The badge a record wears is what the couple reads as "did this reach us". These
 * two mappings are the whole contract between the queue and that sentence.
 */
class DeliveryStatusMappingTest {

    @Test
    fun everyQueuePhaseHasItsOwnWords() {
        assertEquals(MoonLetterRecordStatus.SYNCED, recordStatusOf(EntrySyncPhase.DELIVERED))
        assertEquals(MoonLetterRecordStatus.PENDING_SYNC, recordStatusOf(EntrySyncPhase.QUEUED))
        assertEquals(MoonLetterRecordStatus.UPLOADING_MEDIA, recordStatusOf(EntrySyncPhase.UPLOADING_MEDIA))
        assertEquals(MoonLetterRecordStatus.RETRYING, recordStatusOf(EntrySyncPhase.RETRYING))
        assertEquals(MoonLetterRecordStatus.REJECTED, recordStatusOf(EntrySyncPhase.REJECTED))
    }

    @Test
    fun noTwoPhasesShareTheSameWords() {
        val labels = EntrySyncPhase.entries.map { recordStatusOf(it).label }
        assertEquals(labels.size, labels.distinct().size)
    }
}
