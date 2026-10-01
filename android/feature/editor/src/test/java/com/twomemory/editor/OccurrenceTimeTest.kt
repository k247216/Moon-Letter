package com.twomemory.editor

import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A record's 发生时间 is the writer's answer rather than the clock's, so the two pure
 * functions that turn a tapped day and hour into the instant the database stores are
 * the whole contract here: read the zone wrong and the story lands on another day for
 * both phones, which no later screen can undo.
 */
class OccurrenceTimeTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun utcDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun thePickedMorningIsStoredAsThatMorningInTheRecordsOwnZone() {
        val firstOfOctober = utcDay(2026, 10, 1)

        assertEquals(Instant.parse("2026-10-01T00:30:00Z"), pickedOccurrence(firstOfOctober, 8, 30, shanghai))
        assertEquals(Instant.parse("2026-10-01T08:30:00Z"), pickedOccurrence(firstOfOctober, 8, 30, ZoneId.of("UTC")))
    }

    @Test
    fun theStoredInstantReadsBackAsTheDayTheWriterPicked() {
        val picked = pickedOccurrence(utcDay(2026, 10, 1), 8, 30, shanghai)

        assertEquals("2026年10月1日 · 08:30", occurrenceLabel(picked, "Asia/Shanghai"))
    }

    @Test
    fun anEarlierEveningInUtcIsStillTheWritersOwnDay() {
        val justAfterMidnight = pickedOccurrence(utcDay(2026, 10, 1), 0, 5, shanghai)

        assertEquals("2026年10月1日 · 00:05", occurrenceLabel(justAfterMidnight, "Asia/Shanghai"))
        assertEquals("2026年9月30日 · 16:05", occurrenceLabel(justAfterMidnight, "UTC"))
    }

    @Test
    fun aSingleDigitDayAndMinuteKeepOneShape() {
        val picked = pickedOccurrence(utcDay(2026, 1, 2), 7, 5, ZoneId.of("UTC"))

        assertEquals("2026年1月2日 · 07:05", occurrenceLabel(picked, "UTC"))
    }

    @Test
    fun anUnreadableZoneNameGivesTheRecordTheSameDateEverywhereElse() {
        val instant = Instant.parse("2026-10-01T00:30:00Z")

        assertEquals(ZoneId.of("UTC"), occurrenceZone("Mars/Valles"))
        assertEquals(occurrenceLabel(instant, "UTC"), occurrenceLabel(instant, "Mars/Valles"))
    }

    @Test
    fun theEditorCarriesThisPhonesZoneInsteadOfOneSomeoneTypedInCode() {
        assertEquals(ZoneId.systemDefault().id, EditorUiState().timezone)
    }

    @Test
    fun aMomentNobodyPickedIsNeverClaimedAsOneThatWas() {
        val viewModel = EditorViewModel()
        assertFalse(viewModel.state.value.occurrenceEdited)

        viewModel.restore("傍晚散步", "想跟你说一件事")
        assertFalse(viewModel.state.value.occurrenceEdited)

        val picked = Instant.parse("2026-09-30T16:05:00Z")
        viewModel.updateOccurrence(picked)
        assertTrue(viewModel.state.value.occurrenceEdited)
        assertEquals(picked, viewModel.state.value.occurrenceTime)
    }

    @Test
    fun aRestoredDraftKeepsTheMomentItsWriterChose() {
        val viewModel = EditorViewModel()
        val chosen = Instant.parse("2026-09-30T16:05:00Z")

        viewModel.restore("傍晚散步", "想跟你说一件事", emptyList(), chosen)

        assertEquals(chosen, viewModel.state.value.occurrenceTime)
        assertTrue(viewModel.state.value.occurrenceEdited)
    }
}
