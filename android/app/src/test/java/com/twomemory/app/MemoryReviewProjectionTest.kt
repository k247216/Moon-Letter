package com.twomemory.app

import com.twomemory.database.EntryEntity
import com.twomemory.database.EntryBlockEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryReviewProjectionTest {
    private val me = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val other = "00000000-0000-0000-0000-000000000002"
    private val space = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private fun entry(id: String, date: String) = EntryEntity(id, space.toString(), me.toString(), "PERSONAL", "PUBLISHED",
        LocalDate.parse(date).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(), "Asia/Shanghai", "那一天")
    private fun project(entries: List<EntryEntity>, blocks: List<EntryBlockEntity> = emptyList()) =
        buildReviewMemories(entries, blocks, me, space, SyncSession.Names("我", "你"))

    @Test fun bookKeepsOlderMonthsAndOrdersThemChronologically() {
        val pages = project(listOf(entry("new", "2026-10-11"), entry("old", "2026-08-01")))
        assertEquals(listOf("old", "new"), pages.map { it.id })
        assertEquals(listOf(LocalDate.of(2026,8,1), LocalDate.of(2026,10,11)), pages.map { it.date })
    }

    @Test fun unreadableRecordsNeverEnterTheBook() {
        val base = entry("shared", "2026-10-11")
        val pages = project(listOf(base, base.copy(id="own-private", visibility="PRIVATE"),
            base.copy(id="other-private", authorId=other, visibility="PRIVATE"),
            base.copy(id="foreign", coupleId=UUID.randomUUID().toString()),
            base.copy(id="deleted", deleted=true), base.copy(id="draft", state="DRAFT"),
            base.copy(id="locked", state="CAPSULE_LOCKED")))
        assertEquals(setOf("shared", "own-private"), pages.map { it.id }.toSet())
    }

    @Test fun bookCarriesRealMediaAndAuthorOrderWithoutDeletedBlocks() {
        val row = entry("one", "2026-10-11").copy(mode="COLLABORATIVE")
        val blocks = listOf(
            EntryBlockEntity("photo", "one", "IMAGE", 2, other, "{}", "asset-1"),
            EntryBlockEntity("text", "one", "TEXT", 1, me.toString(), "{\"text\":\"完整的那一天\"}", null),
            EntryBlockEntity("removed", "one", "TEXT", 3, other, "{\"text\":\"不应显示\"}", null, deleted=true))
        val page = project(listOf(row), blocks).single()
        assertEquals(listOf("text", "photo"), page.blocks.map { it.id })
        assertEquals(listOf("我", "你"), page.blocks.map { it.author })
        assertEquals("asset-1", page.blocks.last().assetId)
        assertTrue(page.shared)
    }

    @Test fun sameDaySelectionDoesNotPretendRecentRecordsAreLastYear() {
        val pages = project(listOf(entry("last-year", "2025-10-11"), entry("recent", "2026-10-10"), entry("today", "2026-10-11")))
        assertEquals(listOf("last-year"), historicalToday(pages, LocalDate.of(2026,10,11)).map { it.id })
        assertTrue(historicalToday(pages, LocalDate.of(2026,10,12)).isEmpty())
    }
}
