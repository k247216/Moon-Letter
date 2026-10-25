package com.twomemory.app

import com.twomemory.model.*
import org.json.JSONObject

internal fun notePaperJson(paper: NotePaperAppearance): JSONObject = JSONObject().put("version", 1)
    .put("style", paper.style.name).put("color", paper.color.name)

internal fun notePaperFromPayload(payload: String?): NotePaperAppearance = runCatching {
    val paper = JSONObject(payload.orEmpty()).optJSONObject("notePaper") ?: return@runCatching NotePaperAppearance()
    if (paper.optInt("version") != 1) return@runCatching NotePaperAppearance()
    NotePaperAppearance(
        runCatching { NotePaperStyle.valueOf(paper.optString("style")) }.getOrDefault(NotePaperStyle.TAPE),
        runCatching { NotePaperColor.valueOf(paper.optString("color")) }.getOrDefault(NotePaperColor.CREAM))
}.getOrDefault(NotePaperAppearance())

internal fun notePaperOf(blocks: List<com.twomemory.database.EntryBlockEntity>): NotePaperAppearance =
    notePaperFromPayload(blocks.firstOrNull { it.type == "TEXT" }?.payload)
