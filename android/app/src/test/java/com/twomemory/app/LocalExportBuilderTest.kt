package com.twomemory.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.AppDatabase
import com.twomemory.database.CommentEntity
import com.twomemory.database.EntryBlockEntity
import com.twomemory.database.EntryEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalExportBuilderTest {
    private val ownId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val partnerId = "22222222-2222-2222-2222-222222222222"
    private val entryId = "33333333-3333-3333-3333-333333333333"
    private val coupleId = "44444444-4444-4444-4444-444444444444"

    @Test
    fun nestedLocalPathsAndCredentialKeysNeverEnterEitherExport() = withDatabase { db ->
        insertEntry(db)
        val nested = JSONObject()
            .put("localPath", "/data/user/0/private-top.jpg")
            .put("Access_Token", "ACCESS_SECRET")
            .put("password", "PASSWORD_SECRET")
            .put("credential", JSONObject().put("value", "CREDENTIAL_SECRET"))
            .put("metadata", JSONObject()
                .put("localPath", "/data/user/0/private-nested.jpg")
                .put("Authorization", "Bearer AUTH_SECRET")
                .put("children", JSONArray().put(JSONObject()
                    .put("localPath", "C:\\private\\array.jpg")
                    .put("refreshToken", "REFRESH_SECRET")
                    .put("pairingSecret", "PAIRING_SECRET"))))
        insertBlock(db, "IMAGE", nested.toString())

        val document = export(db)

        assertAbsent(document, "localPath", "private-top.jpg", "private-nested.jpg", "array.jpg",
            "Access_Token", "password", "credential", "Authorization", "refreshToken", "pairingSecret",
            "ACCESS_SECRET", "PASSWORD_SECRET", "CREDENTIAL_SECRET", "AUTH_SECRET", "REFRESH_SECRET", "PAIRING_SECRET")
        assertEquals("IMAGE", record(document).getJSONArray("blocks").getJSONObject(0).getString("type"))
    }

    @Test
    fun credentialBearingMusicUrlsAreRejectedInJsonAndMarkdown() = withDatabase { db ->
        insertEntry(db)
        val unsafeUrls = listOf(
            "https://user:USERINFO_SECRET@music.example/song",
            "https://music.example/song?token=TOKEN_SECRET",
            "https://music.example/song?%61uthorization=ENCODED_SECRET",
            "https://music.example/song#access_token=FRAGMENT_SECRET",
            "https://music.example/song?X-Amz-Signature=AMZ_SECRET",
            "https://music.example/song?X-Oss-Credential=OSS_SECRET",
            "https://music.example/song?password=PASSWORD_SECRET",
            "https://music.example/song?AWSAccessKeyId=KEY_SECRET",
            "https://music.example/song?client_secret=CLIENT_SECRET",
            "https://music.example/song?accesskey=ACCESSKEY_SECRET",
            "https://music.example/song?signature=SIGNATURE_SECRET",
            "https://music.example/song?auth=AUTH_SECRET",
        )
        unsafeUrls.forEachIndexed { index, url ->
            insertBlock(db, "MUSIC", JSONObject().put("title", "晚风").put("artist", "阿屿")
                .put("url", url).put("link", url).toString(), index.toLong())
        }

        val document = export(db)

        assertAbsent(document, "USERINFO_SECRET", "TOKEN_SECRET", "ENCODED_SECRET", "FRAGMENT_SECRET",
            "AMZ_SECRET", "OSS_SECRET", "PASSWORD_SECRET", "KEY_SECRET", "CLIENT_SECRET",
            "ACCESSKEY_SECRET", "SIGNATURE_SECRET", "AUTH_SECRET")
        val blocks = record(document).getJSONArray("blocks")
        assertEquals(unsafeUrls.size, blocks.length())
        for (index in unsafeUrls.indices) {
            val payload = blocks.getJSONObject(index).getJSONObject("payload")
            assertEquals("晚风", payload.getString("title"))
            assertEquals("阿屿", payload.getString("artist"))
            assertFalse(unsafeUrls[index], document.markdown.contains(unsafeUrls[index]))
            assertFalse(unsafeUrls[index], payload.optString("url") == unsafeUrls[index])
            assertFalse(unsafeUrls[index], payload.optString("link") == unsafeUrls[index])
        }
        assertTrue(document.markdown.contains("晚风"))
        assertTrue(document.markdown.contains("阿屿"))
    }

    @Test
    fun localAndNonWebMusicUrlsNeverEnterEitherExport() = withDatabase { db ->
        insertEntry(db)
        val unsafeUrls = listOf(
            "file:///data/user/0/private-song.mp3",
            "content://com.twomemory.app/private-song",
            "javascript:alert('UNSAFE_SCRIPT')",
            "https://music.example/song?%ZZ=INVALID_ENCODING",
        )
        unsafeUrls.forEachIndexed { index, url ->
            insertBlock(db, "MUSIC", JSONObject().put("url", url).toString(), index.toLong())
        }

        assertAbsent(export(db), "private-song", "UNSAFE_SCRIPT", "INVALID_ENCODING")
    }

    @Test
    fun ordinaryMusicNameArtistAndPublicUrlSurviveExport() = withDatabase { db ->
        insertEntry(db)
        insertBlock(db, "MUSIC", JSONObject().put("title", "夜航").put("artist", "小满")
            .put("url", "https://music.example/song?id=42#chorus").toString())
        insertBlock(db, "MUSIC", JSONObject().put("name", "晚风").put("artist", "阿屿")
            .put("link", "https://music.example/song?id=43").toString(), 1)

        val document = export(db)
        val blocks = record(document).getJSONArray("blocks")

        assertEquals("夜航", blocks.getJSONObject(0).getJSONObject("payload").getString("title"))
        assertEquals("小满", blocks.getJSONObject(0).getJSONObject("payload").getString("artist"))
        assertEquals("https://music.example/song?id=42#chorus",
            blocks.getJSONObject(0).getJSONObject("payload").getString("url"))
        assertEquals("晚风", blocks.getJSONObject(1).getJSONObject("payload").getString("name"))
        assertEquals("阿屿", blocks.getJSONObject(1).getJSONObject("payload").getString("artist"))
        assertEquals("https://music.example/song?id=43",
            blocks.getJSONObject(1).getJSONObject("payload").getString("link"))
        listOf("夜航", "小满", "晚风", "阿屿", "https://music.example/song?id=42#chorus",
            "https://music.example/song?id=43").forEach { assertTrue(it, document.markdown.contains(it)) }
    }

    @Test
    fun malformedJsonCannotFallBackToRawPathsOrCredentials() = withDatabase { db ->
        insertEntry(db)
        listOf("TEXT", "IMAGE", "MUSIC", "UNKNOWN").forEachIndexed { index, type ->
            insertBlock(db, type,
                "{\"localPath\":\"/data/user/0/MALFORMED_PRIVATE\",\"token\":\"MALFORMED_SECRET\",",
                index.toLong())
        }

        val document = export(db)

        assertAbsent(document, "MALFORMED_PRIVATE", "MALFORMED_SECRET", "localPath", "token")
        val blocks = record(document).getJSONArray("blocks")
        assertEquals(4, blocks.length())
        for (index in 0 until blocks.length()) {
            assertTrue(blocks.getJSONObject(index).get("payload") is JSONObject)
        }
    }

    @Test
    fun normalTextIsPreservedEvenWhenItMentionsSensitiveKeyNames() = withDatabase { db ->
        insertEntry(db)
        val text = "我写下 token、password 和 localPath 这些词。\n  credential 是今天学习的单词。\n晚风 & 回忆 <3"
        insertBlock(db, "TEXT", JSONObject().put("text", text)
            .put("token", "OUTSIDE_TEXT_SECRET").toString())
        insertBlock(db, "TEXT", JSONObject().put("text", "").toString(), 1)

        val document = export(db)
        val blocks = record(document).getJSONArray("blocks")

        assertEquals(text, blocks.getJSONObject(0).getJSONObject("payload").getString("text"))
        assertEquals("", blocks.getJSONObject(1).getJSONObject("payload").getString("text"))
        assertTrue(document.markdown.contains(text))
        assertAbsent(document, "OUTSIDE_TEXT_SECRET", "{\"text\":\"\"}")
    }

    @Test
    fun machineJsonRetainsVisibilityAndExactOccurrenceEpochMillis() = withDatabase { db ->
        insertEntry(db, visibility = "PRIVATE", epochMillis = 1756728000123L)
        insertEntry(db, id = "shared-entry", visibility = "SHARED", epochMillis = 1756728000987L)

        val records = JSONObject(export(db).json).getJSONArray("records")
        val byId = (0 until records.length()).associate {
            records.getJSONObject(it).let { row -> row.getString("id") to row }
        }

        assertEquals(2, byId.size)
        assertEquals("PRIVATE", byId.getValue(entryId).getString("visibility"))
        assertEquals(1756728000123L, byId.getValue(entryId).getLong("occurredAtEpochMillis"))
        assertEquals("SHARED", byId.getValue("shared-entry").getString("visibility"))
        assertEquals(1756728000987L, byId.getValue("shared-entry").getLong("occurredAtEpochMillis"))
        assertEquals("Asia/Shanghai", byId.getValue(entryId).getString("timezone"))
    }

    @Test
    fun machineJsonRetainsExactCommentCreationEpochMillis() = withDatabase { db ->
        insertEntry(db)
        db.commentDao().insert(CommentEntity("comment-one", entryId, partnerId, "我也记得", null, 1756728000456L))
        db.commentDao().insert(CommentEntity("comment-two", entryId, ownId.toString(), "我们下次再去", "comment-one", 1756728000789L))

        val document = export(db)
        val comments = record(document).getJSONArray("comments")

        assertEquals(2, comments.length())
        assertEquals("comment-one", comments.getJSONObject(0).getString("id"))
        assertEquals(1756728000456L, comments.getJSONObject(0).getLong("createdAtEpochMillis"))
        assertEquals(1756728000789L, comments.getJSONObject(1).getLong("createdAtEpochMillis"))
        assertEquals("我也记得", comments.getJSONObject(0).getString("body"))
        assertEquals("我们下次再去", comments.getJSONObject(1).getString("body"))
        assertTrue(document.markdown.contains("我也记得"))
        assertTrue(document.markdown.contains("我们下次再去"))
    }

    private fun withDatabase(test: suspend (AppDatabase) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            test(db)
        } finally {
            db.close()
        }
    }

    private suspend fun insertEntry(
        db: AppDatabase,
        id: String = entryId,
        visibility: String = "SHARED",
        epochMillis: Long = 1756728000123L,
    ) {
        db.entryDao().insertEntry(EntryEntity(id, coupleId, ownId.toString(), "PERSONAL", "PUBLISHED",
            epochMillis, "Asia/Shanghai", "雨天", visibility = visibility))
    }

    private suspend fun insertBlock(db: AppDatabase, type: String, payload: String, orderKey: Long = 0) {
        db.entryDao().insertBlocks(listOf(EntryBlockEntity("block-$orderKey", entryId, type, orderKey,
            ownId.toString(), payload, null)))
    }

    private suspend fun export(db: AppDatabase) = LocalExportBuilder.build(db, ownId, "我方姓名", "对方姓名")

    private fun record(document: LocalExportDocument): JSONObject =
        JSONObject(document.json).getJSONArray("records").getJSONObject(0)

    private fun assertAbsent(document: LocalExportDocument, vararg forbidden: String) {
        forbidden.forEach {
            assertFalse("JSON leaked $it", document.json.contains(it))
            assertFalse("Markdown leaked $it", document.markdown.contains(it))
        }
    }
}
