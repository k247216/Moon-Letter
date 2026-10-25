package com.twomemory.app

import android.app.Application
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveBindingTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mounted = AtomicReference<MountedState?>()

    private data class MountedState(
        val binding: SyncSession.BindingSnapshot,
        val store: IndependentAnniversaryStore,
        val routeIdentity: UUID,
        val readingPosition: Int,
        val unsentDraft: String,
    )

    @Test
    fun mountedBindingRefreshesAfterBackgroundAToBToAWithoutRenderingIntermediateB() {
        val first = mountBoundContent()
        // Block the UI while both writes finish so no composition can observe B.
        compose.runOnIdle {
            onBackgroundThread {
                SyncSession.save(context, "synthetic-other-owner", UUID.randomUUID(), UUID.randomUUID(), first.binding.session.baseUrl)
                saveSession(first.binding.session)
            }
        }
        assertFreshMountedBinding(first)
    }

    @Test
    fun mountedBindingRefreshesWhenSameTokenReturnsTheExactSameSessionValue() {
        val first = mountBoundContent()
        compose.runOnIdle { onBackgroundThread { saveSession(first.binding.session) } }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Plain Session equality must not hide a new binding epoch",
                first.binding.session, SyncSession.load(context))
        }
        assertFreshMountedBinding(first)
    }

    @Test
    fun tokenRefreshRecreatesOnlyTheBindingStoreAndPreservesMountedRouteState() {
        val first = mountBoundContent()
        compose.runOnIdle {
            onBackgroundThread { saveSession(first.binding.session, "synthetic-refreshed-token") }
        }
        assertFreshMountedBinding(first)
        compose.runOnIdle {
            val current = requireNotNull(mounted.get())
            assertEquals("synthetic-refreshed-token", current.binding.session.token)
            assertEquals(first.binding.session.coupleId, current.binding.session.coupleId)
            assertEquals(first.binding.session.userId, current.binding.session.userId)
            assertEquals(first.binding.session.storageOrigin, current.binding.session.storageOrigin)
        }
    }

    private fun mountBoundContent(): MountedState {
        SyncSession.save(context, "synthetic-live-binding", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:9")
        compose.setContent {
            // These represent caller-owned route/reading/draft state outside the Store.
            val routeIdentity = remember { UUID.randomUUID() }
            val readingPosition = remember { mutableStateOf(37) }
            val unsentDraft = remember { mutableStateOf("尚未提交的正文") }
            val binding = rememberLiveBinding(context)
            val store = remember(binding) { binding?.let { IndependentAnniversaryStore.store(context, it) } }
            SideEffect {
                mounted.set(binding?.let {
                    MountedState(it, requireNotNull(store), routeIdentity, readingPosition.value, unsentDraft.value)
                })
            }
            Text("generation=${binding?.generation}; page=${readingPosition.value}; draft=${unsentDraft.value}")
        }
        compose.waitForIdle()
        return requireNotNull(mounted.get())
    }

    private fun assertFreshMountedBinding(first: MountedState) {
        compose.waitForIdle()
        try {
            compose.waitUntil(10_000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                mounted.get()?.binding == SyncSession.captureBinding(context)
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            val expected = SyncSession.captureBinding(context)
            val actual = mounted.get()?.binding
            throw AssertionError("Mounted epoch=${actual?.generation}, current epoch=${expected?.generation}, sameSession=${actual?.session == expected?.session}", timeout)
        }
        compose.runOnIdle {
            val expected = requireNotNull(SyncSession.captureBinding(context))
            val current = requireNotNull(mounted.get())
            assertTrue("The fixture must really advance the binding generation", expected.generation > first.binding.generation)
            assertFalse("The old Store binding must be invalidated", SyncSession.isCurrentBinding(context, first.binding))
            assertEquals("Mounted content must receive the fresh snapshot without remounting", expected, current.binding)
            assertTrue(SyncSession.isCurrentBinding(context, current.binding))
            assertNotSame("Only the binding-owned Store must be replaced", first.store, current.store)
            assertEquals(first.routeIdentity, current.routeIdentity)
            assertEquals(37, current.readingPosition)
            assertEquals("尚未提交的正文", current.unsentDraft)
            // Exercise a real Store: a stale binding would silently refuse this edit.
            val edit = IndependentAnniversaryDraft(title = "刷新后可编辑", year = 2020)
            current.store.editDraft(edit)
            assertEquals(edit, current.store.state.value.draft)
        }
    }

    private fun saveSession(session: SyncSession.Session, token: String = session.token) {
        SyncSession.save(context, token, session.coupleId, session.userId, session.baseUrl)
    }

    private fun onBackgroundThread(action: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { action() } catch (error: Throwable) { failure.set(error) }
        }
        worker.start()
        worker.join(5_000)
        assertFalse("Binding fixture must finish before releasing the UI thread", worker.isAlive)
        failure.get()?.let { throw AssertionError("Binding fixture failed before the behavior assertion", it) }
    }
}
