package com.twomemory.sync

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** One record the weekly re-encounter could surface: identity plus when it happened. */
data class ReviewCandidate(val entryId: String, val occurredAtEpochMillis: Long)

/**
 * The whole of 每周回看: once a week, one record that is clearly earlier than
 * this week, nothing else. No aggregation, no scoring, no generated text, and
 * no counter of any kind survives a call here.
 */
object WeeklyReview {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** "明显更早": a record younger than this is still part of this week. */
    const val MIN_AGE_DAYS = 8L

    /** The age the re-encounter reaches for first; everything else falls back from here. */
    const val TARGET_AGE_DAYS = 365L

    private const val TARGET_AGE_MILLIS = TARGET_AGE_DAYS * DAY_MILLIS

    /**
     * Returns the single record worth re-encountering, or null when nothing
     * qualifies. Null is the quiet skip: a young archive must produce no
     * notification and no "本周没有内容" placeholder.
     *
     * Among records that are clearly older than this week, the one closest to a
     * year old wins, so "一年前的今天" is what appears when it exists and the
     * search falls back gradually as the archive grows. A tie goes to the more
     * recent record, which is the one she would remember writing.
     */
    fun pick(candidates: List<ReviewCandidate>, nowEpochMillis: Long): ReviewCandidate? {
        val minAgeMillis = MIN_AGE_DAYS * DAY_MILLIS
        return candidates
            .filter { it.occurredAtEpochMillis <= nowEpochMillis - minAgeMillis }
            .minWithOrNull(
                compareBy<ReviewCandidate>(
                    { kotlin.math.abs((nowEpochMillis - it.occurredAtEpochMillis) - TARGET_AGE_MILLIS) },
                    { -it.occurredAtEpochMillis },
                ),
            )
    }

    /**
     * Millis from [fromEpochMillis] until the next [dayOfWeek] at [hourOfDay]
     * local time. WorkManager fires inside a window rather than on the exact
     * minute, so this only decides when the first review is due; afterwards the
     * weekly period carries it forward.
     */
    fun millisUntilNext(
        fromEpochMillis: Long,
        dayOfWeek: DayOfWeek,
        hourOfDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromEpochMillis), zone)
        var next = now.withHour(hourOfDay).withMinute(0).withSecond(0).withNano(0)
        while (next.isBefore(now) || next.dayOfWeek != dayOfWeek) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis()
    }
}
