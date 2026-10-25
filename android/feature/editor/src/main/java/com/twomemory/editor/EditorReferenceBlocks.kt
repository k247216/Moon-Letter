package com.twomemory.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.AudioMemoryCard
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.MusicMemoryCard
import com.twomemory.designsystem.PolaroidFrame
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.VideoMemoryCard
import com.twomemory.model.BlockType
import java.util.UUID

/** Published content stays separate from the new perspective and its editable media. */
@Composable
internal fun EditorReferenceBlocks(
    blocks: List<EditorReferenceBlock>,
    currentUserId: UUID?,
    ownName: String,
    partnerName: String?,
) {
    val ordered = blocks.sortedBy { it.orderKey }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ordered.forEachIndexed { index, block -> key(block.id) {
            val mine = currentUserId != null && block.authorId == currentUserId
            val accent = when {
                block.authorId == null -> TwoMemoryColors.WarmBeigeMuted
                mine -> TwoMemoryColors.WarmBeigeAccent
                else -> TwoMemoryColors.WarmBeigeSage
            }
            val name = when {
                block.authorId == null -> "成员"
                mine -> ownName
                else -> partnerName?.takeIf { it.isNotBlank() } ?: "对方"
            }
            Column(Modifier.fillMaxWidth().semantics {
                contentDescription = "共同记录参考块：${block.id}"
            }.drawBehind {
                drawLine(accent.copy(alpha = .45f), Offset(1.dp.toPx(), 0f),
                    Offset(1.dp.toPx(), size.height), 1.dp.toPx())
            }.padding(start = 18.dp, top = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (index == 0 || ordered[index - 1].authorId != block.authorId) {
                    Text("${name}的视角 · 已写下的内容", style = TwoMemoryTypography.caption, color = accent)
                }
                val payload = runCatching { org.json.JSONObject(block.payload.orEmpty()) }.getOrNull()
                when (block.type) {
                    BlockType.TEXT -> Text(block.text.orEmpty(), style = TwoMemoryTypography.body)
                    BlockType.IMAGE -> PolaroidFrame(modifier = Modifier.fillMaxWidth().height(220.dp), photo = {
                        EntryPhoto(localPath = block.localPath, assetId = block.assetId,
                            modifier = Modifier.fillMaxSize(), contentDescription = "共同记录里的照片",
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            useNaturalAspectRatio = false)
                    })
                    BlockType.AUDIO -> AudioMemoryCard(localPath = block.localPath, assetId = block.assetId,
                        modifier = Modifier.fillMaxWidth(), title = payload?.optString("title").orEmpty())
                    BlockType.VIDEO -> VideoMemoryCard(localPath = block.localPath, assetId = block.assetId,
                        modifier = Modifier.fillMaxWidth())
                    BlockType.MUSIC -> MusicMemoryCard(title = payload?.optString("title").orEmpty(),
                        artist = payload?.optString("artist").orEmpty(),
                        url = payload?.optString("url").orEmpty().ifBlank { payload?.optString("link").orEmpty() },
                        modifier = Modifier.fillMaxWidth())
                    BlockType.LOCATION -> Text(payload?.optString("city").orEmpty().ifBlank { "地点信息暂不可用" },
                        style = TwoMemoryTypography.body, color = accent)
                }
            }
        } }
    }
}
