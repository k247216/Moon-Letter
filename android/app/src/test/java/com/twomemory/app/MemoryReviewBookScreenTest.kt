package com.twomemory.app

import androidx.activity.compose.setContent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.BlockType
import com.twomemory.timeline.EntryBlockUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.io.File
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoryReviewBookScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val pages = listOf(
        ReviewMemoryUi("one", "2026年9月1日", "20:18", "我", "一起去看月亮", "晚风很轻", date=LocalDate.of(2026,9,1),
            blocks=listOf(EntryBlockUi("text", BlockType.TEXT, "晚风很轻", null, null, "我"))),
        ReviewMemoryUi("two", "2026年10月11日", "19:00", "你", "给你带了热奶茶", "今天也想你", date=LocalDate.of(2026,10,11)))

    @Test fun bookCanNavigateOlderMonthAndOpenTheOriginalRecord() {
        var opened: String? = null
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            MemoryReviewScreen(MemoryReviewRoute.WEEKLY_SUMMARY, pages, {}, { opened=it })
        } } }
        compose.onNodeWithText("翻翻我们的回忆").assertIsDisplayed()
        compose.onNodeWithText("按月").performClick()
        compose.onNodeWithContentDescription("选择回忆月份").performClick()
        compose.onNodeWithText("2026年9月").performClick()
        compose.onNodeWithText("一起去看月亮").assertIsDisplayed()
        compose.onNodeWithText("读完整这一页").performClick()
        compose.runOnIdle { assertEquals("one", opened) }
    }

    @Test fun noHistoricalTodayStillOffersAWorkingWayIntoTheBook() {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            MemoryReviewScreen(MemoryReviewRoute.PAST_TODAY, pages, {}, {})
        } } }
        compose.onNodeWithText("翻翻其他日子").performClick()
        compose.onNodeWithText("翻翻我们的回忆").assertIsDisplayed()
        compose.onNodeWithText("给你带了热奶茶").assertIsDisplayed()
    }

    @Test fun pastTodayEmptyStateReturnsAfterExploringAndReenteringSavedRoute() =
        verifyPastTodayReentry(hasHistoricalPage=false)

    @Test fun pastTodayHistoricalPageReturnsAfterExploringAndReenteringSavedRoute() =
        verifyPastTodayReentry(hasHistoricalPage=true)

    private fun verifyPastTodayReentry(hasHistoricalPage: Boolean) {
        val route=mutableStateOf<MemoryReviewRoute?>(MemoryReviewRoute.PAST_TODAY)
        val historical=pages.last().copy(id="historical", dateLabel="2025年10月11日",
            date=LocalDate.of(2025,10,11), title="去年同一天的月亮")
        val memories=if(hasHistoricalPage) pages+historical else pages
        compose.activity.runOnUiThread {compose.activity.setContent {TwoMemoryTheme {
            val holder=rememberSaveableStateHolder()
            val current=route.value
            if(current!=null) holder.SaveableStateProvider(current.name) {
                MemoryReviewScreen(current,memories,{route.value=null},{},LocalDate.of(2026,10,11),
                    onOpenBook={route.value=MemoryReviewRoute.WEEKLY_SUMMARY})
            } else TextButton(onClick={route.value=MemoryReviewRoute.PAST_TODAY}) {
                Text("再次打开过去的今天")
            }
        } } }
        compose.onNodeWithText("再遇见这一天").assertIsDisplayed()
        if(hasHistoricalPage) compose.onNodeWithText("去年同一天的月亮").assertIsDisplayed()
        else compose.onNodeWithText("这一天，还在等回忆慢慢长大").assertIsDisplayed()
        compose.onNodeWithText("翻翻其他日子").performClick()
        compose.onNodeWithText("翻翻我们的回忆").assertIsDisplayed()
        compose.runOnIdle {assertEquals(MemoryReviewRoute.WEEKLY_SUMMARY,route.value)}
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("再次打开过去的今天").performClick()
        compose.onNodeWithText("再遇见这一天").assertIsDisplayed()
        compose.onNodeWithText("翻翻我们的回忆").assertDoesNotExist()
        if(hasHistoricalPage) compose.onNodeWithText("去年同一天的月亮").assertIsDisplayed()
        else compose.onNodeWithText("这一天，还在等回忆慢慢长大").assertIsDisplayed()
        compose.runOnIdle {assertEquals(MemoryReviewRoute.PAST_TODAY,route.value)}
    }

    @Test fun earlierWeekCanBeSelectedWithoutWaitingForAnAnniversary() {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            MemoryReviewScreen(MemoryReviewRoute.WEEKLY_SUMMARY,pages,{},{},LocalDate.of(2026,10,11))
        } } }
        compose.onNodeWithContentDescription("选择回忆周次").performClick()
        compose.onNodeWithText("2026年8月31日 — 9月6日").performClick()
        compose.onNodeWithText("一起去看月亮").assertIsDisplayed()
        compose.onNodeWithText("给你带了热奶茶").assertDoesNotExist()
    }

    @Test fun capturesHandbookPhoneWithRealImageAsset() = captureBook("memory-book-phone.png")

    @Test fun decoratedBookCanTurnPagesWithoutChangingTheOriginalEntry() {
        var opened: String? = null
        val pair=listOf(pages.last(), pages.last().copy(id="third",title="一起走回家"))
        compose.activity.runOnUiThread {compose.activity.setContent {TwoMemoryTheme {
            MemoryReviewScreen(MemoryReviewRoute.WEEKLY_SUMMARY,pair,{},{opened=it},LocalDate.of(2026,10,11))
        } } }
        compose.onNodeWithContentDescription("回忆册书页").assertIsDisplayed()
        compose.onNodeWithText("下一页").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithText("一起走回家").assertIsDisplayed()
        compose.onNodeWithText("读完整这一页").performClick()
        compose.runOnIdle {assertEquals("third",opened)}
    }

    @Test fun eachCityAnnotationBelongsToItsOwnContribution() {
        val page=pages.last().copy(city="武汉", blocks=listOf(
            EntryBlockUi("first-city",BlockType.LOCATION,null,null,null,"我","{\"city\":\"武汉\"}"),
            EntryBlockUi("second-city",BlockType.LOCATION,null,null,null,"你","{\"city\":\"杭州\"}")))
        compose.activity.runOnUiThread {compose.activity.setContent {TwoMemoryTheme {
            MemoryReviewScreen(MemoryReviewRoute.WEEKLY_SUMMARY,listOf(page),{},{},LocalDate.of(2026,10,11))
        } } }
        compose.onAllNodesWithText("武汉").assertCountEquals(1)
        compose.onNodeWithText("杭州").assertIsDisplayed()
    }

    @Test fun removingAPhotoClosesItsAlreadyOpenViewer() {
        val bitmap=BitmapFactory.decodeResource(compose.activity.resources, com.twomemory.designsystem.R.drawable.moonletter_cover_puppy)
        val file=File(compose.activity.filesDir,"withdrawn-photo.png")
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}; bitmap.recycle()
        val memory=mutableStateOf(pages.last().copy(blocks=listOf(EntryBlockUi("image",BlockType.IMAGE,null,file.absolutePath,null,"我"))))
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            MemoryReviewScreen(MemoryReviewRoute.WEEKLY_SUMMARY,listOf(memory.value),{},{},LocalDate.of(2026,10,11))
        } } }
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("翻看回忆里的照片").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("翻看回忆里的照片").performClick()
        compose.onNodeWithText("合上照片").assertIsDisplayed()
        compose.runOnIdle {memory.value=memory.value.copy(blocks=emptyList())}
        compose.onNodeWithText("合上照片").assertDoesNotExist()
    }

    @Test @Config(qualifiers="w960dp-h720dp-mdpi")
    fun capturesHandbookTabletWithRealImageAsset() = captureBook("memory-book-tablet.png")

    private fun captureBook(name: String) {
        val image = BitmapFactory.decodeResource(compose.activity.resources, com.twomemory.designsystem.R.drawable.moonletter_cover_puppy)
        val local = File(compose.activity.filesDir, "review-fixture.png")
        local.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
        val memory = pages.last().copy(title="把晚风写给你", shared=true, blocks=listOf(
            EntryBlockUi("writing",BlockType.TEXT,"走过一条安静的小路，想把这一刻留给你。",null,null,"我"),
            EntryBlockUi("photo",BlockType.IMAGE,null,local.absolutePath,null,"我"),
            EntryBlockUi("reply",BlockType.TEXT,"那就把平凡的一天，也写进我们的手书。",null,null,"你")))
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            MemoryReviewScreen(MemoryReviewRoute.WEEKLY_SUMMARY,listOf(memory),{},{},LocalDate.of(2026,10,11))
        } } }
        compose.onNodeWithText("把晚风写给你").assertIsDisplayed()
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("翻看回忆里的照片").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        val photoBounds=compose.onNodeWithContentDescription("翻看回忆里的照片").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Fixed preview must not overflow its 220dp page slot",photoBounds.height<=220.5f)
        val writingBounds=compose.onNodeWithText("走过一条安静的小路，想把这一刻留给你。").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Photo must not overlap handwriting",writingBounds.bottom<=photoBounds.top)
        val captured=compose.onRoot().captureToImage().asAndroidBitmap()
        val marginPixel=captured.getPixel(photoBounds.center.x.toInt(),photoBounds.top.toInt()-4)
        org.junit.Assert.assertEquals("Photo pixels must stay inside their measured slot, not paint over the margin above",0xfffffcf5.toInt(),marginPixel)
        val output=File("../../releases/v43/$name"); output.parentFile!!.mkdirs()
        output.outputStream().use {captured.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
