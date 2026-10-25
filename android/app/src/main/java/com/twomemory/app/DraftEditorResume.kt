package com.twomemory.app

import com.twomemory.editor.EditorViewModel
import com.twomemory.model.EntryMode

/** Open only this member's scoped library; never claim a perspective draft. */
internal fun openDraftEditor(library: DraftLibrary, mode: EntryMode, editor: EditorViewModel,
    fresh: Boolean=false): String {
    val active=library.activeDraft(mode)
    if(fresh && active!=null) library.markManualSaved(active.id)
    val saved=if(fresh) null else active
    editor.reset()
    saved?.draft?.let {draft ->
        editor.restore(draft.title,draft.body,draft.photos,draft.occurrence,draft.attachments,draft.visibility)
    }
    return saved?.id ?: java.util.UUID.randomUUID().toString()
}
