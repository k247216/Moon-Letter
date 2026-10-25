package com.twomemory.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryBookmarkStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val origin = "https://synthetic-${UUID.randomUUID()}.invalid"
    private val couple = UUID.randomUUID().toString()
    private val user = UUID.randomUUID().toString()
    private val first = UUID.randomUUID().toString()
    private val second = UUID.randomUUID().toString()

    private fun store(endpoint: String = origin, space: String = couple, actor: String = user) =
        MemoryBookmarkStore(context, endpoint, space, actor)

    @Test fun togglePersistsStableIdsAcrossStoreRecreation() {
        assertTrue(store().ids().isEmpty())
        assertEquals(setOf(first), store().toggle(first))
        assertEquals(setOf(first), store().ids())
    }

    @Test fun secondToggleRemovesOnlyThatBookmark() {
        val bookmarks = store()
        bookmarks.toggle(first)
        assertEquals(setOf(first, second), bookmarks.toggle(second))
        assertEquals(setOf(second), bookmarks.toggle(first))
        assertEquals(setOf(second), store().ids())
    }

    @Test fun originSpaceAndUserEachIsolatePersonalBookmarks() {
        assertEquals(setOf(first), store().toggle(first))
        val otherOrigin = store("https://different-${UUID.randomUUID()}.invalid")
        val otherSpace = store(space = UUID.randomUUID().toString())
        val otherUser = store(actor = UUID.randomUUID().toString())
        for (other in listOf(otherOrigin, otherSpace, otherUser)) {
            assertTrue(other.ids().isEmpty())
            assertEquals(setOf(second), other.toggle(second))
            assertEquals(setOf(first), store().ids())
        }
    }

    @Test fun removeIsIdempotentAndLeavesOtherRecordIdsIntact() {
        val bookmarks = store()
        bookmarks.toggle(first)
        assertEquals(setOf(first, second), bookmarks.toggle(second))
        assertEquals(setOf(second), bookmarks.remove(first))
        assertEquals(setOf(second), bookmarks.remove(first))
        assertEquals(setOf(second), store().ids())
    }

    @Test fun separateStoreInstancesDoNotOverwriteNewlyAddedIds() {
        val firstInstance = store()
        val secondInstance = store()
        assertEquals(setOf(first), firstInstance.toggle(first))
        assertEquals(setOf(first, second), secondInstance.toggle(second))
        assertEquals(setOf(first, second), firstInstance.ids())
    }

    @Test fun trailingSlashOnTheSameOriginDoesNotCreateAnotherBookmarkScope() {
        assertEquals(setOf(first), store().toggle(first))
        assertEquals(setOf(first), store("$origin/").ids())
    }

    @Test fun falseCommitAfterMemoryMutationDoesNotAddAnUnconfirmedBookmark() {
        assertEquals(setOf(first), store().toggle(first))
        val failNext = AtomicBoolean(true)
        val bookmarks = MemoryBookmarkStore(falseMutatingCommitContext(failNext), origin, couple, user)
        assertThrows(IllegalStateException::class.java) { bookmarks.toggle(second) }
        assertEquals("Failed addition must restore the confirmed IDs in memory", setOf(first), bookmarks.ids())
        assertEquals("Reopening must not expose the failed addition", setOf(first), store().ids())
    }

    @Test fun falseCommitAfterMemoryMutationDoesNotRemoveAConfirmedBookmark() {
        store().toggle(first)
        assertEquals(setOf(first, second), store().toggle(second))
        val failNext = AtomicBoolean(true)
        val bookmarks = MemoryBookmarkStore(falseMutatingCommitContext(failNext), origin, couple, user)
        assertThrows(IllegalStateException::class.java) { bookmarks.remove(first) }
        assertEquals("Failed removal must restore both confirmed IDs", setOf(first, second), bookmarks.ids())
        assertEquals(setOf(first, second), store().ids())
    }

    /** Only replaces disk-commit acknowledgement; real preferences still mutate/read at the boundary. */
    private fun falseMutatingCommitContext(failNext: AtomicBoolean): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val prefs = super.getSharedPreferences(name, mode)
            if (!name.startsWith("moon_letter_bookmarks_")) return prefs
            return object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val delegate = prefs.edit()
                    return object : SharedPreferences.Editor by delegate {
                        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                            delegate.putStringSet(key, values)
                            return this
                        }
                        override fun clear(): SharedPreferences.Editor {
                            delegate.clear()
                            return this
                        }
                        override fun commit(): Boolean {
                            if (failNext.compareAndSet(true, false)) {
                                delegate.apply()
                                return false
                            }
                            return delegate.commit()
                        }
                    }
                }
            }
        }
    }
}
