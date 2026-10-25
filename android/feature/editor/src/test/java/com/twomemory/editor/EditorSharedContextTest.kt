package com.twomemory.editor

import com.twomemory.model.BlockType
import com.twomemory.model.EntryVisibility
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorSharedContextTest {
    @Test fun originalMomentAndZoneReplaceDraftDateWithoutReplacingUnsentContribution() {
        val vm = EditorViewModel()
        val photo = EditorPhoto(UUID.randomUUID(), "draft-photo.jpg", "image/jpeg")
        val audio = EditorAttachment(UUID.randomUUID(), BlockType.AUDIO, "draft-audio.m4a", "audio/mp4")
        vm.restore("草稿标题", "我的未发送正文", savedPhotos = listOf(photo),
            savedOccurrence = Instant.parse("2026-10-11T12:00:00Z"), savedAttachments = listOf(audio),
            savedVisibility = EntryVisibility.PRIVATE)

        vm.setSharedContext("阿屿", "TA 的原文", "原记录标题", ownPublishedBody = "我的原文",
            occurrenceTime = Instant.parse("2026-09-30T23:30:00Z"), timezone = "Asia/Shanghai")

        val state = vm.state.value
        assertEquals(Instant.parse("2026-09-30T23:30:00Z"), state.occurrenceTime)
        assertEquals("Asia/Shanghai", state.timezone)
        assertEquals("2026-10-01", state.occurrenceTime.atZone(ZoneId.of(state.timezone)).toLocalDate().toString())
        assertTrue(state.occurrenceEdited)
        assertTrue(state.appendingPerspective)
        assertEquals("原记录标题", state.title)
        assertEquals("我的未发送正文", state.body)
        assertEquals(listOf(photo), state.photos)
        assertEquals(listOf(audio), state.attachments)
        assertEquals(EntryVisibility.PRIVATE, state.visibility)
    }

    @Test fun restoringAppendDraftCannotReplaceOriginalRecordMomentWithDraftMoment() {
        val vm = EditorViewModel()
        vm.setSharedContext("阿屿", "TA 的原文", "原记录标题",
            occurrenceTime = Instant.parse("2026-09-30T23:30:00Z"), timezone = "Pacific/Honolulu")
        vm.restore("草稿标题", "我的补写", savedOccurrence = Instant.parse("2026-10-11T12:00:00Z"))
        assertEquals(Instant.parse("2026-09-30T23:30:00Z"), vm.state.value.occurrenceTime)
        assertEquals("Pacific/Honolulu", vm.state.value.timezone)
        assertEquals("原记录标题", vm.state.value.title)
        assertEquals("我的补写", vm.state.value.body)
        assertTrue(vm.state.value.occurrenceEdited)
    }

    @Test fun appendDraftWithoutDateStillKeepsOriginalMomentMarkedAsHistorical() {
        val vm = EditorViewModel()
        vm.setSharedContext("阿屿", "TA 的原文", occurrenceTime = Instant.parse("2026-09-30T23:30:00Z"),
            timezone = "Asia/Shanghai")
        vm.restore("", "我的补写")
        assertEquals(Instant.parse("2026-09-30T23:30:00Z"), vm.state.value.occurrenceTime)
        assertEquals("Asia/Shanghai", vm.state.value.timezone)
        assertTrue(vm.state.value.occurrenceEdited)
        assertEquals("我的补写", vm.state.value.body)
    }

    @Test fun omittedContextMomentKeepsExistingDateForCompatibleCallers() {
        val vm = EditorViewModel()
        vm.updateOccurrence(Instant.parse("2026-09-29T01:00:00Z"))
        val zone = vm.state.value.timezone
        vm.setSharedContext("阿屿", "TA 的原文")
        assertEquals(Instant.parse("2026-09-29T01:00:00Z"), vm.state.value.occurrenceTime)
        assertEquals(zone, vm.state.value.timezone)
        assertTrue(vm.state.value.occurrenceEdited)
    }

    @Test fun existingOwnWritingIsReadOnlyContextNotPartnerOrNewBody() {
        val vm = EditorViewModel()
        vm.setSharedContext("阿屿", "TA 的原文", "共同回忆", ownPublishedBody = "我已经写过的正文")
        assertEquals("我已经写过的正文", vm.state.value.ownPublishedBody)
        assertEquals("TA 的原文", vm.state.value.partnerBody)
        assertEquals("", vm.state.value.body)
        assertFalse(vm.state.value.hasContent)
        vm.updateTitle("不应改动原篇标题")
        assertEquals("共同回忆", vm.state.value.title)
        vm.restore("旧草稿标题", "还没发送的补写")
        assertEquals("共同回忆", vm.state.value.title)
        assertEquals("还没发送的补写", vm.state.value.body)
        vm.reset()
        assertEquals(null, vm.state.value.ownPublishedBody)
    }

    @Test fun existingTitleAndPartnerContextAreNotANewContribution() {
        val vm = EditorViewModel()
        vm.setSharedContext("阿屿", "雨后的彩虹", "雨后的散步")
        assertEquals("雨后的散步", vm.state.value.title)
        assertEquals("", vm.state.value.body)
        assertFalse(vm.state.value.hasContent)
        vm.updateBody("我的补写")
        assertTrue(vm.state.value.hasContent)
        assertEquals("雨后的彩虹", vm.state.value.partnerBody)
    }

    @Test fun refreshingSharedContextDoesNotReplaceOwnDraft() {
        val vm = EditorViewModel()
        vm.restore("共同回忆", "我的未发送正文")
        vm.setSharedContext("阿屿", "TA 的原文")
        assertEquals("共同回忆", vm.state.value.title)
        assertEquals("我的未发送正文", vm.state.value.body)
        vm.reset()
        assertEquals(null, vm.state.value.partnerBody)
        assertFalse(vm.state.value.appendingPerspective)
    }
}
