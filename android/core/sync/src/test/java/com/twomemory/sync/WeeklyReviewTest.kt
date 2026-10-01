package com.twomemory.sync

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeeklyReviewTest {

    private val now: Long = millisOf(2026, 10, 1, 10)

    @Test
    fun pickReturnsNothingForAnArchiveYoungerThanAWeek() {
        val candidates = listOf(candidate("young", 1), candidate("younger", 7))
        assertNull(WeeklyReview.pick(candidates, now))
    }

    @Test
    fun pickTakesTheRecordClosestToAYearOld() {
        val candidates = listOf(candidate("a-month", 30), candidate("almost-a-year", 360), candidate("older", 500))
        assertEquals("almost-a-year", WeeklyReview.pick(candidates, now)?.entryId)
    }

    @Test
    fun pickFallsBackToTheOldestRecordWhenNothingIsThatOldYet() {
        val candidates = listOf(candidate("recent", 20), candidate("oldest", 45))
        assertEquals("oldest", WeeklyReview.pick(candidates, now)?.entryId)
    }

    @Test
    fun pickBreaksATieTowardsTheMoreRecentRecord() {
        val candidates = listOf(candidate("just-under", 360), candidate("just-over", 370))
        assertEquals("just-under", WeeklyReview.pick(candidates, now)?.entryId)
    }

    @Test
    fun pickCountsARecordExactlyOneWeekOld() {
        val candidates = listOf(candidate("exactly-a-week", WeeklyReview.MIN_AGE_DAYS))
        assertEquals("exactly-a-week", WeeklyReview.pick(candidates, now)?.entryId)
    }

    @Test
    fun millisUntilNextWaitsForTheChosenWeekdayAndHour() {
        val millis = WeeklyReview.millisUntilNext(now, DayOfWeek.SUNDAY, 20, ZONE)
        assertEquals(3 * DAY + 10 * HOUR, millis)
    }

    @Test
    fun millisUntilNextReachesLaterOnTheSameDay() {
        val wednesdayMorning = millisOf(2026, 9, 30, 9)
        val millis = WeeklyReview.millisUntilNext(wednesdayMorning, DayOfWeek.WEDNESDAY, 20, ZONE)
        assertEquals(11 * HOUR, millis)
    }

    @Test
    fun millisUntilNextRollsOverToNextWeekOnceTheHourPassed() {
        val wednesdayNight = millisOf(2026, 9, 30, 21)
        val millis = WeeklyReview.millisUntilNext(wednesdayNight, DayOfWeek.WEDNESDAY, 20, ZONE)
        assertEquals(7 * DAY - HOUR, millis)
    }

    private fun candidate(entryId: String, ageDays: Long) =
        ReviewCandidate(entryId, now - ageDays * DAY)

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int) =
        LocalDateTime.of(year, month, day, hour, 0).atZone(ZONE).toInstant().toEpochMilli()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        const val DAY = 24L * 60 * 60 * 1000
        const val HOUR = 60L * 60 * 1000
    }
}
