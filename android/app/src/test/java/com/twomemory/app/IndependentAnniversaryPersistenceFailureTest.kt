package com.twomemory.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndependentAnniversaryPersistenceFailureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun binding(): SyncSession.BindingSnapshot {
        SyncSession.save(context, "synthetic-disk-failure", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:9")
        return requireNotNull(SyncSession.captureBinding(context))
    }

    @Test fun failedDraftCommitRetainsMemoryInputAndShowsUnsavedErrorWithoutThrowingToUi() {
        val binding = binding()
        val store = IndependentAnniversaryStore.store(failingDisk(), binding)
        val draft = IndependentAnniversaryDraft(title = "刚写下的名字", year = 2020)
        val thrown = runCatching { store.editDraft(draft) }.exceptionOrNull()
        assertNull("Disk failure must not escape the UI callback", thrown)
        assertEquals(draft, store.state.value.draft)
        assertNotNull("The UI must honestly say the input is not persisted", store.state.value.error)
        assertNull(IndependentAnniversaryStore.store(context, binding).state.value.draft.year)
        assertNull(store.state.value.confirmed)
    }

    @Test fun failedWriteEpochCommitDoesNotThrowOrInventAckAndLeavesStoreRetryable() = runBlocking {
        val binding = binding()
        val draft = IndependentAnniversaryDraft(year = 2020)
        IndependentAnniversaryStore.store(context, binding).editDraft(draft)
        val store = IndependentAnniversaryStore.store(failingDisk(), binding)
        val result = runCatching { store.save(LocalDate.of(2026, 10, 13)) }
        assertNull("The pre-request disk failure must be reported, not thrown", result.exceptionOrNull())
        assertFalse(result.getOrNull() == true)
        assertFalse(store.state.value.saving)
        assertNotNull(store.state.value.error)
        assertNull(store.state.value.confirmed)
        assertEquals(draft, store.state.value.draft)
        // A failed begin must never strand the shared scope's active-write owner.
        assertTrue(IndependentAnniversaryStore.acceptConfirmed(context, binding, null))
    }

    private fun failingDisk(): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val prefs = super.getSharedPreferences(name, mode)
            return if (name.startsWith("independent_anniversary_")) object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor = FailedCommitEditor(prefs.edit())
            } else prefs
        }
    }

    /** Fault injection is confined to the actual persistence boundary, not store behavior. */
    private class FailedCommitEditor(private val delegate: SharedPreferences.Editor) : SharedPreferences.Editor by delegate {
        override fun putString(key: String, value: String?): SharedPreferences.Editor { delegate.putString(key, value); return this }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor { delegate.putStringSet(key, values); return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { delegate.putInt(key, value); return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { delegate.putLong(key, value); return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { delegate.putFloat(key, value); return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { delegate.putBoolean(key, value); return this }
        override fun remove(key: String): SharedPreferences.Editor { delegate.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { delegate.clear(); return this }
        override fun commit(): Boolean = false
        override fun apply() { throw AssertionError("Store must use checked durable commits") }
    }
}
