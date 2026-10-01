package com.twomemory.app

import android.content.Intent
import android.net.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareIntentParserTest {

    @Test
    fun textShareBecomesOnePersonalDraftPayload() {
        val parsed = ShareIntentParser.parse(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "今天听到的歌")
                putExtra(Intent.EXTRA_TEXT, "https://music.163.com/song?id=123")
            },
        )

        assertEquals("今天听到的歌", parsed?.title)
        assertEquals("https://music.163.com/song?id=123", parsed?.body)
        assertEquals(emptyList(), parsed?.imageUris)
    }

    @Test
    fun multipleImageShareKeepsAllUrisInOrder() {
        val first = Uri.parse("content://share/one")
        val second = Uri.parse("content://share/two")
        val parsed = ShareIntentParser.parse(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            },
        )

        assertEquals(listOf(first, second), parsed?.imageUris)
    }

    @Test
    fun multipleTextShareJoinsEachSharedLine() {
        val parsed = ShareIntentParser.parse(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/plain"
                putCharSequenceArrayListExtra(Intent.EXTRA_TEXT, arrayListOf("第一段", "第二段"))
            },
        )

        assertEquals("第一段\n第二段", parsed?.body)
    }

    @Test
    fun unrelatedIntentDoesNotOpenTheEditor() {
        assertNull(ShareIntentParser.parse(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
    }
}
