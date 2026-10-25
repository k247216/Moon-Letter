package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.model.EntryMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerspectiveDraftIsolationTest {
    @Test fun switchingMembersCannotReadTheOthersUnpublishedPerspective() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val space = UUID.randomUUID(); val a = UUID.randomUUID(); val b = UUID.randomUUID(); val entry = UUID.randomUUID()
        SyncSession.save(base, "token-a", space, a, "http://127.0.0.1:9")
        val first = perspectiveDraftContext(base, entry)
        DraftStore.save(first, EntryMode.COLLABORATIVE, "同一天", "A尚未分享的想法")
        SyncSession.save(base, "token-b", space, b, "http://127.0.0.1:9")
        assertNull(DraftStore.load(perspectiveDraftContext(base, entry), EntryMode.COLLABORATIVE))
        SyncSession.save(base, "token-a", space, a, "http://127.0.0.1:9")
        assertEquals("A尚未分享的想法", DraftStore.load(perspectiveDraftContext(base, entry), EntryMode.COLLABORATIVE)?.body)
        SyncSession.save(base, "token-a", space, a, "http://127.0.0.1:10")
        assertNull(DraftStore.load(perspectiveDraftContext(base, entry), EntryMode.COLLABORATIVE))
    }
    @Test fun supplementDraftDoesNotReplaceANewSharedRecordDraft() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        DraftStore.save(base, EntryMode.COLLABORATIVE, "新文章", "未完成的第一篇")
        val api = runCatching { Class.forName("com.twomemory.app.PerspectiveDraftStoreKt") }.getOrNull()
        assertNotNull("Same-entry writing requires isolated persistent draft storage", api)
        val scoped = api!!.getMethod("perspectiveDraftContext", Context::class.java, UUID::class.java)
            .invoke(null, base, UUID.fromString("00000000-0000-0000-0000-000000000009")) as Context
        DraftStore.save(scoped, EntryMode.COLLABORATIVE, "原文标题", "我的补写")
        assertEquals("未完成的第一篇", DraftStore.load(base, EntryMode.COLLABORATIVE)?.body)
        assertEquals("我的补写", DraftStore.load(scoped, EntryMode.COLLABORATIVE)?.body)
        DraftStore.clear(scoped, EntryMode.COLLABORATIVE)
        assertEquals("未完成的第一篇", DraftStore.load(base, EntryMode.COLLABORATIVE)?.body)
    }
}
