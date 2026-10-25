package com.twomemory.app

import android.graphics.Color as AndroidColor
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryState
import com.twomemory.model.TimelineItem
import com.twomemory.timeline.JournalEntryCard
import com.twomemory.timeline.TimelineEntryUi
import com.twomemory.timeline.TimelineRoute
import com.twomemory.timeline.TimelineScreen
import com.twomemory.timeline.TimelineViewModel
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimelineInitialLoadingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val own = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val source = MutableSharedFlow<List<TimelineItem>>(replay=1)

    @Test fun delayedFirstLocalEmissionShowsLoadingNotAnEmptyArchive() {
        val model=showRoute()
        compose.runOnIdle {
            assertTrue(model.initialLoading.value)
            assertTrue(model.entries.value.isEmpty())
            model.updateNames("小满", "阿屿")
            model.updateReadIds(emptySet())
            model.updateRejectedIds(setOf("unrelated"))
        }
        compose.onNodeWithTag("timeline-initial-loading").assertIsDisplayed()
        assertNoEmptyArchive()
        compose.runOnIdle {assertTrue(model.initialLoading.value)}
    }

    @Test fun firstNonemptyLocalEmissionEndsLoadingAndDisplaysExistingRecord() {
        val model=showRoute()
        compose.runOnIdle {assertTrue(model.initialLoading.value)}
        emit(listOf(record("原来就存着的手帐")))
        compose.waitUntil(5_000) {model.entries.value.size==1}
        compose.runOnIdle {assertFalse(model.initialLoading.value)}
        compose.onNodeWithText("原来就存着的手帐").assertIsDisplayed()
        compose.onNodeWithTag("timeline-initial-loading").assertDoesNotExist()
        assertNoEmptyArchive()
    }

    @Test fun firstEmptyLocalEmissionEndsLoadingAndEnablesGenuineEmptyAction() {
        val model=showRoute()
        compose.runOnIdle {assertTrue(model.initialLoading.value)}
        emit(emptyList())
        compose.runOnIdle {
            assertFalse(model.initialLoading.value)
            assertTrue(model.entries.value.isEmpty())
        }
        compose.onNodeWithTag("timeline-initial-loading").assertDoesNotExist()
        compose.onNodeWithText("时光还留着空白").assertIsDisplayed()
        compose.onNodeWithText("写下第一句").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun laterNamesAndLocalRefreshKeepLoadedRecordsVisibleWithoutRestartingLoading() {
        val model=showRoute()
        val first=record("已经打开的手帐")
        emit(listOf(first))
        compose.waitUntil(5_000) {model.entries.value.size==1}
        compose.runOnIdle {
            assertFalse(model.initialLoading.value)
            model.updateNames("新名字", "阿屿")
            model.updateRejectedIds(setOf(first.id.toString()))
            model.updateReadIds(setOf(first.id.toString()))
        }
        compose.onNodeWithText("已经打开的手帐").assertIsDisplayed()
        assertNoEmptyArchive()
        compose.onNodeWithTag("timeline-initial-loading").assertDoesNotExist()
        emit(listOf(first, record("后来同步到本机的手帐")))
        compose.waitUntil(5_000) {model.entries.value.size==2}
        compose.runOnIdle {
            assertFalse(model.initialLoading.value)
            assertEquals(listOf("已经打开的手帐", "后来同步到本机的手帐"), model.entries.value.map {it.title})
        }
        compose.onNodeWithText("已经打开的手帐").assertIsDisplayed()
        assertNoEmptyArchive()
    }

    @Test fun directScreenCallerStillGetsEmptyStateWithoutOptingIntoLoading() {
        var created=0
        compose.activity.runOnUiThread {compose.activity.setContent {TwoMemoryTheme {
            TimelineScreen(emptyList(), onCreate={created++})
        } } }
        compose.onNodeWithText("写下第一句").performClick()
        compose.runOnIdle {assertEquals(1,created)}
        compose.onNodeWithTag("timeline-initial-loading").assertDoesNotExist()
    }

    @Test fun journalRailRasterKeepsCoralSwayAndDashedGaps() {
        compose.activity.runOnUiThread {compose.activity.setContent {TwoMemoryTheme {
            Box(Modifier.fillMaxWidth().background(Color.White).testTag("rail-fixture")) {
                JournalEntryCard(TimelineEntryUi("rail", "2026年10月13日", "20:18", "我", "手书",
                    body="把平凡的日子，继续写在同一页。", mine=true), onOpen={})
            }
        } } }
        val bitmap=compose.onNodeWithTag("rail-fixture").captureToImage().asAndroidBitmap()
        val ink=mutableListOf<Pair<Int,Int>>()
        val clearRows=mutableListOf<Int>()
        for(y in 32 until bitmap.height-24) {
            var hasInk=false
            for(x in 17..32) {
                val pixel=bitmap.getPixel(x,y)
                if(AndroidColor.red(pixel)-AndroidColor.green(pixel)>20 &&
                    AndroidColor.green(pixel)-AndroidColor.blue(pixel)>5) {
                    ink+=x to y; hasInk=true
                }
            }
            if(!hasInk) clearRows+=y
        }
        assertTrue("The rail must remain visible",ink.isNotEmpty())
        assertTrue("The stitched curve must sway left of its 24dp axis",ink.any {it.first<=20})
        assertTrue("The stitched curve must sway right of its 24dp axis",ink.any {it.first>=28})
        assertTrue("The stroke must keep real dashed gaps",clearRows.isNotEmpty())
        assertTrue("The rail must retain coral at 66% opacity on white",ink.any { (x,y) ->
            val pixel=bitmap.getPixel(x,y)
            kotlin.math.abs(AndroidColor.red(pixel)-228)<=3 &&
                kotlin.math.abs(AndroidColor.green(pixel)-168)<=3 &&
                kotlin.math.abs(AndroidColor.blue(pixel)-152)<=3
        })
    }

    private fun showRoute(): TimelineViewModel {
        lateinit var model: TimelineViewModel
        compose.activity.runOnUiThread {
            model=TimelineViewModel({source}, currentUserId=own, preparationDispatcher=Dispatchers.Unconfined)
            compose.activity.viewModelStore.put("initial-loading-test",model)
            compose.activity.setContent {TwoMemoryTheme {TimelineRoute(model)}}
        }
        compose.waitForIdle()
        return model
    }

    private fun emit(records: List<TimelineItem>) {
        compose.runOnIdle {assertTrue(source.tryEmit(records))}
        compose.waitForIdle()
    }

    private fun assertNoEmptyArchive() {
        compose.onNodeWithText("时光还留着空白").assertDoesNotExist()
        compose.onNodeWithText("写下第一句").assertDoesNotExist()
    }

    private fun record(title: String) = TimelineItem(UUID.randomUUID(), UUID.randomUUID(), EntryMode.PERSONAL,
        EntryState.PUBLISHED, Instant.parse("2026-10-13T12:18:00Z"), "Asia/Shanghai", title, own,
        preview="本机已经保存的正文")
}
