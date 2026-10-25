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
        assertEquals(TwoMemoryIcons.Home, MoonLetterTabs[0].icon)
        assertEquals(TwoMemoryIcons.Album, MoonLetterTabs[1].icon)
        assertEquals(TwoMemoryIcons.Add, MoonLetterTabs[2].icon)
        assertEquals(TwoMemoryIcons.Location, MoonLetterTabs[3].icon)
        assertEquals(TwoMemoryIcons.Couple, MoonLetterTabs[4].icon)
    }

    @Test
    fun recordStatusHasEveryUserVisibleState() {
        assertEquals(
            listOf(
                "草稿",
                "已保存到本机",
                "已保存到本机 · 等待确认",
                "照片还在上传",
                "已保存到本机",
                "服务器拒收了这条",
                "服务器已收下",
                "这条还没存进手机",
            ),
            MoonLetterRecordStatus.entries.map { it.label },
        )
    }

    @Test
    fun bodyTypographyIsReadable() {
        assertTrue(TwoMemoryTypography.body.fontSize.value >= 14f)
    }
}
