package com.twomemory.app

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingMusicShareTest {
    // Reflection lets the existing parser compile at RED; a missing music result
    // is an assertion failure, rather than an unresolved production symbol.
    private fun share(text: String): IncomingShare = assertNotNull(ShareIntentParser.parse(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        },
    ))

    private fun music(share: IncomingShare): Any? = share.javaClass.methods
        .firstOrNull { it.name == "getMusic" }?.invoke(share)

    private fun field(value: Any, name: String): String = value.javaClass
        .getMethod("get${name.replaceFirstChar { it.uppercase() }}").invoke(value) as String

    @Test fun neteaseSongExtractsMetadataAndKeepsOriginalText() {
        val text = "分享周杰伦的单曲《晴天》: https://music.163.com/song?id=186016 (来自@网易云音乐)"
        val incoming = share(text)
        val song = assertNotNull(music(incoming), "Supported song share must expose music metadata")
        assertEquals("晴天", field(song, "title"))
        assertEquals("周杰伦", field(song, "artist"))
        assertEquals("https://music.163.com/song?id=186016", field(song, "url"))
        assertEquals(text, incoming.body)
    }

    @Test fun qqSongExtractsMetadataAndTrimsOnlyLinkPunctuation() {
        val incoming = share("分享周杰伦的歌曲《稻香》 https://y.qq.com/n/ryqq/songDetail/003aAYrm3GE0Ac。")
        val song = assertNotNull(music(incoming))
        assertEquals("稻香", field(song, "title"))
        assertEquals("周杰伦", field(song, "artist"))
        assertEquals("https://y.qq.com/n/ryqq/songDetail/003aAYrm3GE0Ac", field(song, "url"))
    }

    @Test fun providerShortLinkIsPreservedWithoutNetworkResolution() {
        val song = assertNotNull(music(share("分享歌曲《起风了》 https://163cn.tv/AbC123")))
        assertEquals("起风了", field(song, "title"))
        assertEquals("https://163cn.tv/AbC123", field(song, "url"))
    }

    @Test fun bareSongLinkStillProducesAnOpenableSong() {
        val song = assertNotNull(music(share("https://c6.y.qq.com/base/fcgi-bin/u?__=AbC")))
        assertEquals("https://c6.y.qq.com/base/fcgi-bin/u?__=AbC", field(song, "url"))
    }

    @Test fun unknownOrdinaryShareRemainsExactText() {
        val text = "今天的文章 https://example.com/story?id=123"
        val incoming = share(text)
        assertNull(music(incoming))
        assertEquals(text, incoming.body)
    }

    @Test fun providerLookalikesAndUnsafeSchemesRemainOrdinaryShares() {
        listOf(
            "https://music.163.com.evil.example/song?id=1",
            "https://music.163.com@evil.example/song?id=1",
            "javascript:alert('https://music.163.com/song?id=1')",
            "file://music.163.com/song?id=1",
            "https://evil.example/?next=https://music.163.com/song?id=1",
        ).forEach { text ->
            val incoming = share(text)
            assertNull(music(incoming), text)
            assertEquals(text, incoming.body)
        }
    }

    @Test fun providerHomepageIsNotClassifiedAsASong() {
        assertNull(music(share("https://music.163.com/")))
        assertNull(music(share("https://y.qq.com/")))
    }

    @Test fun neteaseHashSongRouteIsSupported() {
        val song = assertNotNull(music(share("https://music.163.com/#/song?id=186016")))
        assertEquals("https://music.163.com/#/song?id=186016", field(song, "url"))
    }

    @Test fun malformedProviderRoutesStayOrdinaryText() {
        listOf(
            "https://music.163.com/song?other=123",
            "https://music.163.com/song?id=",
            "https://y.qq.com/n/ryqq/songDetail/",
            "https://i.y.qq.com/v8/playsong.html",
            "https://y.music.163.com/m/song?unrelated=1",
            "https://music.163.com:8080/song?id=1",
        ).forEach { assertNull(music(share(it)), it) }
    }

    @Test fun qqHtmlAndNeteaseMobileSongRoutesAreSupported() {
        listOf(
            "https://i.y.qq.com/v8/playsong.html?songmid=003aAYrm3GE0Ac",
            "https://y.music.163.com/m/song?id=186016",
        ).forEach { assertEquals(it, field(assertNotNull(music(share(it))), "url")) }
    }

    @Test fun qqMetadataWithArtistAfterTitleIsExtracted() {
        val song = assertNotNull(music(share("分享歌曲《稻香》-周杰伦 https://c6.y.qq.com/base/fcgi-bin/u?__=AbC")))
        assertEquals("周杰伦", field(song, "artist"))
    }

    @Test fun subjectCanSupplyMetadataWithoutChangingTheBody() {
        val incoming = assertNotNull(ShareIntentParser.parse(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "分享周杰伦的单曲《晴天》")
            putExtra(Intent.EXTRA_TEXT, "https://music.163.com/song?id=186016")
        }))
        assertEquals("晴天", field(assertNotNull(music(incoming)), "title"))
        assertEquals("https://music.163.com/song?id=186016", incoming.body)
    }

    @Test fun providerShareColonDoesNotRequireASpaceBeforeUrl() {
        val song = assertNotNull(music(share("分享周杰伦的单曲《晴天》:https://music.163.com/song?id=186016")))
        assertEquals("晴天", field(song, "title"))
        assertEquals("https://music.163.com/song?id=186016", field(song, "url"))
    }
}
