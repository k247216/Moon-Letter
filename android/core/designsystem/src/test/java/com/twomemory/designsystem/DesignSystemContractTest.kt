package com.twomemory.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignSystemContractTest {
    @Test
    fun tabsKeepGuidedOrderAndSemanticLabels() {
        assertEquals(
            listOf("timeline", "album", "create", "map", "couple"),
            MoonLetterTabs.map { it.key },
        )
        assertEquals(
            listOf("时光", "相册", "＋记录", "地图", "我们"),
            MoonLetterTabs.map { it.label },
        )
        assertEquals(
            MoonLetterTabs.map { it.label },
            MoonLetterTabs.map { it.contentDescription },
        )
        assertEquals(TwoMemoryIcons.Image, MoonLetterTabs[0].icon)
        assertEquals(TwoMemoryIcons.Camera, MoonLetterTabs[1].icon)
        assertEquals(TwoMemoryIcons.Add, MoonLetterTabs[2].icon)
        assertEquals(TwoMemoryIcons.Location, MoonLetterTabs[3].icon)
        assertEquals(TwoMemoryIcons.Music, MoonLetterTabs[4].icon)
    }

    @Test
    fun recordStatusHasEveryUserVisibleState() {
        assertEquals(
            listOf("草稿", "已保存到本机", "等待同步", "已同步", "同步失败，点击重试"),
            MoonLetterRecordStatus.entries.map { it.label },
        )
    }

    @Test
    fun bodyTypographyIsReadable() {
        assertTrue(TwoMemoryTypography.body.fontSize.value >= 14f)
    }
}
