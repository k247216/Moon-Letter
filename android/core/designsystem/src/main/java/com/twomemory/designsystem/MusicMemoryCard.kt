package com.twomemory.designsystem

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.net.URI

/** Shared editor/reader boundary: only ordinary web pages without embedded credentials. */
fun isSafeMusicLink(url: String): Boolean = runCatching { URI(url.trim()) }.getOrNull()?.let {
    it.scheme?.lowercase() in listOf("https", "http") && !it.host.isNullOrBlank() && it.userInfo == null
} == true

/** The system opens the lawful shared page; this component never fetches audio. */
@Composable
fun MusicMemoryCard(title: String, artist: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val safe = remember(url) { isSafeMusicLink(url) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    Surface(modifier.fillMaxWidth(), color = TwoMemoryColors.WarmBeigeBackground,
        shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(44.dp)) {
                    val radius = size.minDimension / 2
                    drawCircle(TwoMemoryColors.WarmBeigeInk)
                    for (fraction in listOf(.62f, .77f, .9f)) {
                        drawCircle(TwoMemoryColors.WarmBeigeSurface.copy(alpha = .22f), radius * fraction,
                            style = Stroke(width = 1.dp.toPx()))
                    }
                    drawCircle(TwoMemoryColors.WarmBeigeAccentSoft, radius * .38f)
                }
                Icon(TwoMemoryIcons.Music, "一起听过", Modifier.size(14.dp), tint = TwoMemoryColors.WarmBeigeInk)
            }
            Column(Modifier.weight(1f)) {
                Text(title.ifBlank { "一起听这首歌" }, style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeInk)
                if (artist.isNotBlank()) Text(artist, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                if (error != null) Text(error!!, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error)
                else if (!safe) Text(if (url.isBlank()) "只记下这首歌" else "链接不可用", style = TwoMemoryTypography.caption,
                    color = TwoMemoryColors.WarmBeigeMuted)
            }
            if (safe) TextButton(onClick = {
                val launched = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }.isSuccess
                if (!launched) error = "没有可打开歌曲链接的应用"
            }) { Text("打开歌曲") }
        }
    }
}
