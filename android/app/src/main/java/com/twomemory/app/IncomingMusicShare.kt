package com.twomemory.app

import java.net.URI

data class IncomingMusicShare(val title: String, val artist: String, val url: String, val source: String = "")

/** No redirects, scraping, or audio fetching: classify only known provider song URLs. */
object IncomingMusicParser {
    fun parse(text: String): IncomingMusicShare? {
        val candidates = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(text)
        val url = candidates.mapNotNull { match ->
            // A URL embedded inside another token/query/unsafe scheme is not a share URL.
            val prefix = text.substring(0, match.range.first).takeLastWhile { !it.isWhitespace() }
            if (prefix.any { it in "='\"/?" } ||
                (':' in prefix && prefix.substringBefore(':').matches(Regex("[A-Za-z][A-Za-z0-9+.-]*")))) return@mapNotNull null
            val candidate = match.value.trimEnd('。', '，', ',', '.', '!', '！', ')', '）', '》', ';', '；')
            val uri = runCatching { URI(candidate) }.getOrNull() ?: return@mapNotNull null
            if (uri.userInfo != null || uri.port !in listOf(-1, 80, 443)) return@mapNotNull null
            val host = uri.host?.lowercase() ?: return@mapNotNull null
            val path = uri.path.orEmpty()
            fun parameter(query: String?, name: String): String? = query.orEmpty().split('&')
                .firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")?.takeIf { it.isNotBlank() }
            fun songId(query: String?): Boolean = parameter(query, "id")?.matches(Regex("[1-9][0-9]*")) == true
            fun qqId(): Boolean = parameter(uri.rawQuery, "songmid")?.matches(Regex("[A-Za-z0-9]+")) == true ||
                parameter(uri.rawQuery, "songid")?.matches(Regex("[1-9][0-9]*")) == true
            val song = when (host) {
                "music.163.com" -> (path == "/song" && songId(uri.rawQuery)) ||
                    (path in listOf("", "/") && uri.rawFragment.orEmpty().substringBefore('?') == "/song" &&
                        songId(uri.rawFragment?.substringAfter('?', "")))
                "y.music.163.com" -> (path in listOf("/m/song", "/song") && songId(uri.rawQuery)) ||
                    path.matches(Regex("/m/song/[1-9][0-9]*/?"))
                "163cn.tv" -> path.matches(Regex("/[A-Za-z0-9_-]+/?"))
                "y.qq.com", "i.y.qq.com" -> path.matches(Regex("/n/ryqq/songDetail/[A-Za-z0-9]+/?")) ||
                    (path in listOf("/n/yqq/song.html", "/n/yqq/songDetail.html", "/v8/playsong.html") && qqId()) ||
                    path.matches(Regex("/n/yqq/song/[A-Za-z0-9]+\\.html"))
                "c6.y.qq.com", "c.y.qq.com" -> path == "/base/fcgi-bin/u" && parameter(uri.rawQuery, "__") != null
                else -> false
            }
            candidate.takeIf { song }
        }.firstOrNull() ?: return null
        val title = Regex("《([^》]+)》").find(text)?.groupValues?.get(1).orEmpty().trim()
        val artist = (Regex("分享\\s*(.+?)的(?:单曲|歌曲|音乐)\\s*《").find(text)
            ?.groupValues?.get(1) ?: Regex("》\\s*[-—–]\\s*(.+?)(?=\\s*https?://|$)").find(text)
            ?.groupValues?.get(1)).orEmpty().trim()
        val host = URI(url).host.lowercase()
        val source = if (host == "163cn.tv" || host.endsWith("music.163.com")) "netease" else "qq"
        return IncomingMusicShare(title, artist, url, source)
    }
}
