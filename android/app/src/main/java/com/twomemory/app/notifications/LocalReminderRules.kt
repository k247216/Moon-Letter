package com.twomemory.app.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class LocalReminderPlan(val key: String, val atMillis: Long)

/** Calendar rules only; actual background delivery may be delayed by Android. */
object LocalReminderRules {
    fun anniversary(
        name: String,
        date: String,
        repeatsYearly: Boolean,
        time: String,
        nowMillis: Long,
        zone: ZoneId,
    ): LocalReminderPlan? = runCatching {
        if (name.isBlank()) return null
        val rule = LocalDate.parse(date.trim())
        val hour = LocalTime.parse(time.trim())
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        var target = (if (repeatsYearly) rule.withYear(today.year) else rule)
            .atTime(hour).atZone(zone).toInstant().toEpochMilli()
        if (target < nowMillis) {
            if (!repeatsYearly) return null
            target = rule.withYear(today.year + 1).atTime(hour).atZone(zone).toInstant().toEpochMilli()
        }
        LocalReminderPlan("anniversary:${name.trim()}:${rule}:$target", target)
    }.getOrNull()

    fun capsule(
        title: String,
        unlockDate: String,
        locked: Boolean,
        nowMillis: Long,
        zone: ZoneId,
    ): LocalReminderPlan? = runCatching {
        if (!locked || title.isBlank()) return null
        val value = unlockDate.trim()
        val date = if ('T' in value) LocalDateTime.parse(value) else LocalDate.parse(value).atStartOfDay()
        val target = date.atZone(zone).toInstant().toEpochMilli()
        // A delayed worker/restart can still announce a due, previously unread occurrence.
        LocalReminderPlan("capsule:${title.trim()}:$target", target)
    }.getOrNull()
}
