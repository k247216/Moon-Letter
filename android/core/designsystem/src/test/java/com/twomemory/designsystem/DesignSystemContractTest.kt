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
