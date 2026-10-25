package com.twomemory.editor

import com.twomemory.model.BlockType
import java.util.UUID

/** Original visible block supplied by navigation; never part of a new submission. */
data class EditorReferenceBlock(
    val id: String,
    val authorId: UUID? = null,
    val type: BlockType = BlockType.TEXT,
    val orderKey: Long = 0,
    val text: String? = null,
    val localPath: String? = null,
    val assetId: String? = null,
    val payload: String? = null,
)
