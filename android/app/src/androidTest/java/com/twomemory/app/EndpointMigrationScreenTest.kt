package com.twomemory.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Assert.assertEquals
import java.util.UUID

class EndpointMigrationScreenTest {
    private val compose = createAndroidComposeRule<CloudConnectionActivity>()
    private val fixture = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            SyncSession.save(context, "endpoint-screen-fixture", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:18088")
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)
    @Test fun cancellingPublicTransitionKeepsOldIdentityWithoutReinitializingSpace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = SyncSession.load(context)
        compose.onNodeWithText("打开我们的手账").assertIsDisplayed().performClick()
        compose.onNodeWithText("一起写下日常").assertExists()
        compose.onNodeWithText("再等等").performClick()
        compose.onNodeWithText("一起写下日常").assertDoesNotExist()
        compose.runOnIdle { assertEquals(before, SyncSession.load(context)) }
    }
    @Test fun startupHooksRecoverAlbumWorkForCurrentEndpointWithoutRecordWorker() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SyncSession.sessionPrefs(context).edit().putString("baseUrl", ServerEndpointMigration.PUBLIC_ORIGIN).commit()
        val session = SyncSession.load(context)!!
        val origin = java.security.MessageDigest.getInstance("SHA-256").digest(session.baseUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)
        val name = "moon-letter-album-$origin-${session.coupleId}"
        val manager = androidx.work.WorkManager.getInstance(context)
        manager.cancelUniqueWork(name).result.get()
        // Represents startup after a committed endpoint change with no new-origin work yet.
        SyncSession.installProcessHooks(context)
        val works = manager.getWorkInfosForUniqueWork(name).get()
        org.junit.Assert.assertTrue(works.any { !it.state.isFinished })
        manager.cancelUniqueWork(name).result.get()
    }
}
