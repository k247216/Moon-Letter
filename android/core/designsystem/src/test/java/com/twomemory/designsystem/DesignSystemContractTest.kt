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
            listOf(
                "草稿",
                "已保存到本机",
                "等待同步",
                "照片还在上传",
                "发送没成功，正在自动重试",
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
