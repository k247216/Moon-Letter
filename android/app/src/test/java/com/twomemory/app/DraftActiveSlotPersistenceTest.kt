package com.twomemory.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.twomemory.model.EntryMode
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftActiveSlotPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun id() = UUID.randomUUID().toString()

    @Test fun failedManualFinishThrowsAndRestoresThePreviousActiveStatus() {
        val boundary = ActivePreferenceBoundary(context)
        val scope=id(); val slot=id(); val library=DraftLibrary(boundary,scope)
        val draft=DraftStore.Draft("仍在编辑的页","不能因结束状态写盘失败而丢失恢复入口")
        library.save(slot,EntryMode.PERSONAL,draft)
        val before=boundary.activePreferences(scope).all.toMap()
        boundary.failNextCommit.set(true)

        val error=runCatching { library.markManualSaved(slot) }.exceptionOrNull()

        assertNotNull("Manual finish must report a failed durable write to its caller",error)
        assertEquals(before,boundary.activePreferences(scope).all)
        assertEquals(slot,DraftLibrary(context,scope).activeDraft(EntryMode.PERSONAL)?.id)
        assertEquals(draft,library.load(slot)?.draft)
        assertEquals(1,library.list().size)
    }

    @Test fun failedActivationThrowsAndRestoresThePreviouslyActivePage() {
        val boundary=ActivePreferenceBoundary(context)
        val scope=id(); val old=id(); val next=id(); val library=DraftLibrary(boundary,scope)
        library.save(old,EntryMode.PERSONAL,DraftStore.Draft("原活动页","原正文"))
        library.save(next,EntryMode.PERSONAL,DraftStore.Draft("手动恢复候选页","候选正文"),activate=false)
        val before=boundary.activePreferences(scope).all.toMap()
        boundary.failNextCommit.set(true)

        val error=runCatching { library.activate(next,EntryMode.PERSONAL) }.exceptionOrNull()

        assertNotNull("Failed activation cannot silently switch the interruption slot",error)
        assertEquals(before,boundary.activePreferences(scope).all)
        assertEquals(old,DraftLibrary(context,scope).activeDraft(EntryMode.PERSONAL)?.id)
        assertEquals(2,library.list().size)
    }

    @Test fun repeatedAutosavesOfTheSameActivePageDoNotRewriteTheSidecar() {
        val boundary=ActivePreferenceBoundary(context)
        val library=DraftLibrary(boundary,id()); val slot=id()
        library.save(slot,EntryMode.PERSONAL,DraftStore.Draft("正在写","第一句"))
        boundary.commits.set(0); boundary.applies.set(0)

        repeat(6) { library.save(slot,EntryMode.PERSONAL,DraftStore.Draft("正在写","新增正文 $it")) }
        library.activate(slot,EntryMode.PERSONAL)

        assertEquals("Unchanged active identity needs no durable sidecar rewrite",0,boundary.commits.get())
        assertEquals("Autosave must not replace durable state with repeated async writes",0,boundary.applies.get())
        assertEquals("新增正文 5",library.load(slot)?.draft?.body)
        assertEquals(slot,library.activeDraft(EntryMode.PERSONAL)?.id)
    }

    @Test fun changedActiveStatusUsesCheckedCommitAndAlreadyClosedStatusDoesNotWrite() {
        val boundary=ActivePreferenceBoundary(context)
        val library=DraftLibrary(boundary,id()); val slot=id()
        library.save(slot,EntryMode.PERSONAL,DraftStore.Draft("手动恢复页","原正文"),activate=false)
        library.activate(slot,EntryMode.PERSONAL)
        assertEquals(1,boundary.commits.get())
        assertEquals(0,boundary.applies.get())
        library.markManualSaved(slot)
        assertEquals(2,boundary.commits.get())
        assertEquals(0,boundary.applies.get())
        library.markManualSaved(slot)
        assertEquals("An already closed slot must not be rewritten",2,boundary.commits.get())
        assertNotNull(library.load(slot))
    }

    /** Intercepts only sidecar persistence; old draft/index serializers stay real. */
    private class ActivePreferenceBoundary(base: Context) : ContextWrapper(base) {
        val commits=AtomicInteger()
        val applies=AtomicInteger()
        val failNextCommit=AtomicBoolean()

        fun activePreferences(scope: String): SharedPreferences =
            super.getSharedPreferences("moon_letter_draft_active_${recordingScopeKey(scope)}",Context.MODE_PRIVATE)

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val prefs=super.getSharedPreferences(name,mode)
            if (!name.startsWith("moon_letter_draft_active_")) return prefs
            return object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor =
                    ObservedEditor(prefs.edit(),commits,applies,failNextCommit)
            }
        }
    }

    private class ObservedEditor(
        private val delegate: SharedPreferences.Editor,
        private val commits: AtomicInteger,
        private val applies: AtomicInteger,
        private val failNextCommit: AtomicBoolean,
    ) : SharedPreferences.Editor by delegate {
        override fun putString(key: String, value: String?): SharedPreferences.Editor { delegate.putString(key,value); return this }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor { delegate.putStringSet(key,values); return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { delegate.putInt(key,value); return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { delegate.putLong(key,value); return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { delegate.putFloat(key,value); return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { delegate.putBoolean(key,value); return this }
        override fun remove(key: String): SharedPreferences.Editor { delegate.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { delegate.clear(); return this }
        override fun apply() { applies.incrementAndGet(); delegate.apply() }
        override fun commit(): Boolean {
            commits.incrementAndGet()
            val reject=failNextCommit.getAndSet(false)
            // Model commit's in-memory mutation before a failed persistence result.
            val written=delegate.commit()
            return written && !reject
        }
    }
}
