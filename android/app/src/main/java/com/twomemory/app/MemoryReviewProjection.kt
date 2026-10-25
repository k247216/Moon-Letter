package com.twomemory.app

import com.twomemory.database.EntryEntity
import com.twomemory.database.EntryBlockEntity
import java.time.LocalDate
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.twomemory.model.BlockType
import com.twomemory.timeline.EntryBlockUi
import org.json.JSONObject

internal fun buildReviewMemories(entries: List<EntryEntity>, blocks: List<EntryBlockEntity>, ownId: UUID?,
    spaceId: UUID?, names: SyncSession.Names): List<ReviewMemoryUi> {
    if (ownId == null || spaceId == null) return emptyList()
    fun author(id: String) = if (id == ownId.toString()) names.own.ifBlank { "我" } else names.partner.ifBlank { "对方" }
    val grouped = blocks.filterNot { it.deleted }.groupBy { it.entryId }
    return entries.asSequence().filter {
        it.coupleId == spaceId.toString() && it.state == "PUBLISHED" && !it.deleted &&
            (it.visibility == "SHARED" || (it.visibility == "PRIVATE" && it.authorId == ownId.toString()))
    }.sortedWith(compareBy<EntryEntity> { it.occurredAtEpochMillis }.thenBy { it.id }).map { entry ->
        val local = Instant.ofEpochMilli(entry.occurredAtEpochMillis)
            .atZone(runCatching { ZoneId.of(entry.occurredTimezone) }.getOrElse { ZoneId.of("UTC") })
        val content = grouped[entry.id].orEmpty().sortedBy { it.orderKey }.mapNotNull { block ->
            val type = runCatching { BlockType.valueOf(block.type) }.getOrNull() ?: return@mapNotNull null
            val payload = runCatching { JSONObject(block.payload) }.getOrElse { JSONObject() }
            EntryBlockUi(block.id, type, payload.optString("text").takeIf { type == BlockType.TEXT && it.isNotBlank() },
                payload.optString("localPath").takeIf(String::isNotBlank), block.assetId, author(block.authorId),
                // Only display metadata is forwarded; never render arbitrary JSON as prose.
                JSONObject().apply { listOf("title", "artist", "url", "link", "city", "cityName", "name").forEach { key ->
                    (payload.opt(key) as? String)?.let { put(key, it) }
                } }.toString(), mine=block.authorId==ownId.toString(),
                authorId=runCatching { UUID.fromString(block.authorId) }.getOrNull())
        }
        ReviewMemoryUi(entry.id, local.format(REVIEW_DATE), local.format(REVIEW_TIME), author(entry.authorId),
            entry.title?.takeIf(String::isNotBlank), content.mapNotNull { it.text }.joinToString("\n\n"),
            city=content.firstOrNull { it.type == BlockType.LOCATION }?.let {
                val json=JSONObject(it.payload.orEmpty())
                json.optString("city").ifBlank { json.optString("cityName") }.ifBlank { json.optString("name") }.takeIf(String::isNotBlank)
            }, date=local.toLocalDate(), blocks=content, shared=entry.mode == "COLLABORATIVE")
    }.toList()
}

internal fun historicalToday(pages: List<ReviewMemoryUi>, today: LocalDate): List<ReviewMemoryUi> =
    pages.filter { page -> page.date?.let { it.year < today.year && it.month == today.month && it.dayOfMonth == today.dayOfMonth }
        ?: page.id.startsWith("past:") }

private val REVIEW_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val REVIEW_TIME = DateTimeFormatter.ofPattern("HH:mm")
