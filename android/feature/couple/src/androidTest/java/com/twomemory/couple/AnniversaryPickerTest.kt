package com.twomemory.couple

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AnniversaryPickerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectingCalendarDateSavesWithoutTypingAFormat() {
        var saved: AnniversaryDraft? = null
        compose.setContent { TwoMemoryTheme { Box(Modifier.fillMaxSize()) {
            RelationshipToolsScreen(CoupleToolRoute.ANNIVERSARY, onBack = {}, initialAnniversaryName = "相遇",
                onSaveAnniversary = { saved = it })
        } } }
        compose.onNodeWithText("纪念日期 · 选择日期").performClick()
        onView(withId(android.R.id.button1)).perform(click())
        compose.onNodeWithText("保存纪念日").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LocalDate.now().toString(), saved?.date) }
        compose.onAllNodesWithText("发送测试通知").assertCountEquals(0)
    }
    @Test fun relationshipHomeShowsCountdownWithoutOpeningEditor() {
        val date = LocalDate.now().plusDays(12).toString()
        compose.setContent { TwoMemoryTheme { Box(Modifier.fillMaxSize()) {
            CoupleScreen(CoupleUiState(), {}, {}, anniversaryName = "去见你", anniversaryDate = date, anniversaryRepeats = false)
        } } }
        compose.onNodeWithText("距离 $date 还有 12 天").assertIsDisplayed()
    }
}
