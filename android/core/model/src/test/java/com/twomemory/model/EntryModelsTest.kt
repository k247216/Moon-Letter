package com.twomemory.model

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Instant
import java.util.UUID

class EntryModelsTest {
    @Test
    fun occurrenceTimezoneAndUuidRoundTrip() {
        val entry = LocalEntryCommand(
            coupleId = UUID.randomUUID(), authorId = UUID.randomUUID(), mode = EntryMode.PERSONAL,
            occurredAt = Instant.parse("2026-09-30T12:18:00Z"), occurredTimezone = "Asia/Shanghai")
        assertEquals("Asia/Shanghai", entry.occurredTimezone)
        assertEquals(Instant.parse("2026-09-30T12:18:00Z"), entry.occurredAt)
    }
}
