package com.twomemory.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.twomemory.couple.CoupleRoute
import com.twomemory.couple.CoupleViewModel
import com.twomemory.database.AppDatabase
import com.twomemory.designsystem.MoonLetterBottomNavigation
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.editor.EditorViewModel
import com.twomemory.editor.PersonalEditorScreen
import com.twomemory.editor.SharedEditorRoute
import com.twomemory.editor.rememberEditorPhotoActions
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryState
import com.twomemory.model.TimelineItem
import com.twomemory.timeline.EntryDetailRoute
import com.twomemory.timeline.EntryDetailViewModel
import com.twomemory.timeline.TimelineRoute
import com.twomemory.timeline.TimelineViewModel
import org.json.JSONArray
import org.json.JSONObject

private fun com.twomemory.database.EntryEntity.toTimelineItem(
    preview: String?,
    photo: com.twomemory.model.TimelinePhoto?,
) = TimelineItem(
    id = java.util.UUID.fromString(id),
    coupleId = java.util.UUID.fromString(coupleId),
    mode = EntryMode.valueOf(mode),
    state = EntryState.valueOf(state),
    occurredAt = java.time.Instant.ofEpochMilli(occurredAtEpochMillis),
    occurredTimezone = occurredTimezone,
    title = title,
    authorId = java.util.UUID.fromString(authorId),
    preview = preview,
    photo = photo,
)

private fun payloadOf(block: com.twomemory.database.EntryBlockEntity) =
    runCatching { JSONObject(block.payload) }.getOrElse { JSONObject() }

private fun previewOf(blocks: List<com.twomemory.database.EntryBlockEntity>): String? =
    blocks.firstOrNull { it.type == "TEXT" }?.let { block ->
        payloadOf(block).optString("text").takeIf { it.isNotBlank() } ?: block.payload
    }

private fun photoOf(blocks: List<com.twomemory.database.EntryBlockEntity>) =
    blocks.firstOrNull { it.type == "IMAGE" && (it.assetId != null || it.entryPath != null) }?.let { block ->
        com.twomemory.model.TimelinePhoto(block.entryPath, block.assetId)
    }

/** The copy this phone kept, when the record's picture is still readable here. */
private val com.twomemory.database.EntryBlockEntity.entryPath: String?
    get() = payloadOf(this).optString("localPath").takeIf {
        it.isNotBlank() && java.io.File(it).isFile
    }

private fun com.twomemory.database.EntryBlockEntity.toEntryBlock(): com.twomemory.model.EntryBlock {
    val payload = payloadOf(this)
    return com.twomemory.model.EntryBlock(
        id = java.util.UUID.fromString(id),
        type = com.twomemory.model.BlockType.valueOf(type),
        orderKey = orderKey,
        text = if (type == "TEXT") payload.optString("text").takeIf { it.isNotBlank() } ?: payload.toString() else null,
        localPath = entryPath,
        assetId = assetId,
    )
}

private fun com.twomemory.database.CommentEntity.toEntryComment() = com.twomemory.model.EntryComment(
    id = java.util.UUID.fromString(id),
    entryId = java.util.UUID.fromString(entryId),
    authorId = runCatching { java.util.UUID.fromString(authorId) }.getOrNull(),
    body = body,
    createdAt = java.time.Instant.ofEpochMilli(createdAtEpochMillis),
)

/** Everything one record's page shows, read straight from this device's Room. */
private suspend fun loadEntryDetail(
    database: com.twomemory.database.AppDatabase,
    entryId: String,
): com.twomemory.model.EntryDetail? {
    val entity = database.entryDao().findEntry(entryId) ?: return null
    val blocks = database.entryDao().blocks(entryId)
    return com.twomemory.model.EntryDetail(
        entry = entity.toTimelineItem(previewOf(blocks), photoOf(blocks)),
        blocks = blocks.map { it.toEntryBlock() },
        comments = database.commentDao().commentsForEntry(entryId).map { it.toEntryComment() },
    )
}

/** Unpublished editor text survives process death through SharedPreferences. */
object DraftStore {
    private const val PREFS = "moon_letter_draft"

    fun save(
        context: android.content.Context,
        title: String,
        body: String,
        photos: List<com.twomemory.editor.EditorPhoto> = emptyList(),
        mode: EntryMode = EntryMode.PERSONAL,
    ) {
        if (title.isBlank() && body.isBlank() && photos.isEmpty()) return
        val storedPhotos = JSONArray().apply {
            photos.forEach { photo ->
                put(JSONObject().put("id", photo.id.toString())
                    .put("localPath", photo.localPath)
                    .put("mime", photo.mimeType))
            }
        }
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString("title", title)
            .putString("body", body)
            .putString("photos", storedPhotos.toString())
            .putString("mode", mode.name)
            .apply()
    }

    data class Draft(
        val title: String,
        val body: String,
        val mode: EntryMode,
        val photos: List<com.twomemory.editor.EditorPhoto> = emptyList(),
    )

    fun load(context: android.content.Context): Draft? {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val title = prefs.getString("title", null).orEmpty()
        val body = prefs.getString("body", null).orEmpty()
        val photos = runCatching {
            val array = JSONArray(prefs.getString("photos", "[]").orEmpty())
            (0 until array.length()).map { index ->
                val photo = array.getJSONObject(index)
                com.twomemory.editor.EditorPhoto(
                    id = java.util.UUID.fromString(photo.getString("id")),
                    localPath = photo.getString("localPath"),
                    mimeType = photo.optString("mime", "image/jpeg"),
                )
            }
        }.getOrDefault(emptyList())
        if (title.isBlank() && body.isBlank() && photos.isEmpty()) return null
        val mode = runCatching {
            EntryMode.valueOf(prefs.getString("mode", EntryMode.PERSONAL.name).orEmpty())
        }.getOrDefault(EntryMode.PERSONAL)
        return Draft(title, body, mode, photos)
    }

    fun clear(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().clear().apply()
    }
}

@Composable
fun AppNavigation(onThemeChange: (MoonLetterTheme) -> Unit = {}) {
    val context = LocalContext.current
    var bound by remember { mutableStateOf(SyncSession.load(context) != null) }
    if (!bound) {
        TwoMemoryTheme {
            SetupScreen(onBound = { bound = true })
        }
        return
    }
    var selectedKey by remember { mutableStateOf("timeline") }
    var editingMode by remember { mutableStateOf<EntryMode?>(null) }
    val visualPrefs = remember { context.getSharedPreferences("moon_letter_visuals", android.content.Context.MODE_PRIVATE) }
    var coverUri by remember { mutableStateOf(visualPrefs.getString("coverUri", null)) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            coverUri = uri.toString()
            visualPrefs.edit().putString("coverUri", uri.toString()).apply()
        }
    }
    val coverBitmap = remember(coverUri) {
        coverUri?.let { value ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(value)).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val coupleViewModel = remember { CoupleViewModel() }
    var cachedNames by remember { mutableStateOf(SyncSession.loadNames(context)) }
    val database = remember { AppDatabase.build(context) }
    val timelineViewModel = remember {
        val dao = database.entryDao()
        TimelineViewModel(
            loader = {
                dao.timelineSnapshot().map { entity ->
                    val blocks = dao.blocks(entity.id)
                    entity.toTimelineItem(previewOf(blocks), photoOf(blocks))
                }
            },
            currentUserId = SyncSession.load(context)?.userId,
        )
    }
    var openEntryId by remember { mutableStateOf<String?>(null) }
    val editorViewModel = remember { EditorViewModel() }
    val editorState by editorViewModel.state.collectAsState()

    // Real display names win over the placeholder couple state: the space is
    // read once when the app is entered, and again after a rename succeeds.
    // Typing in the name field must not reach the network.
    LaunchedEffect(Unit) {
        SyncSession.load(context)?.let(SyncSession::installPhotoSource)
        SyncSession.refreshNames(context)
        cachedNames = SyncSession.loadNames(context)
    }
    LaunchedEffect(cachedNames) {
        timelineViewModel.updateNames(own = cachedNames.own, partner = cachedNames.partner)
    }

    // Unpublished text is kept off the critical path: restore on entry, keep
    // on every exit except a successful save.
    LaunchedEffect(editorState.saved) {
        if (editorState.saved) {
            DraftStore.clear(context)
            editingMode = null
            timelineViewModel.refresh()
        }
    }

    if (selectedKey == "timeline" && editingMode == null) {
        LaunchedEffect(selectedKey, editingMode) { timelineViewModel.refresh() }
    }

    editingMode?.let { mode ->
        LaunchedEffect(Unit) {
            if (editorState.saved) editorViewModel.reset()
            DraftStore.load(context)?.let { draft ->
                editingMode = draft.mode
                editorViewModel.restore(draft.title, draft.body, draft.photos)
            }
        }
        BackHandler {
            DraftStore.save(context, editorState.title, editorState.body, editorState.photos, mode)
            editingMode = null
        }
        DisposableEffect(mode) {
            onDispose {
                if (!editorState.saved) {
                    DraftStore.save(context, editorState.title, editorState.body, editorState.photos, mode)
                }
            }
        }
        val closeEditor = {
            DraftStore.save(context, editorState.title, editorState.body, editorState.photos, mode)
            editingMode = null
        }
        val photoActions = rememberEditorPhotoActions(editorViewModel)
        if (mode == EntryMode.PERSONAL) {
            PersonalEditorScreen(
                state = editorState,
                onTitleChange = editorViewModel::updateTitle,
                onBodyChange = editorViewModel::updateBody,
                onPublish = { editorViewModel.publish { state -> SyncSession.publish(context, state, mode) } },
                onClose = closeEditor,
                mode = mode,
                onModeChange = { editingMode = it },
                photoActions = photoActions,
            )
        } else {
            SharedEditorRoute(
                state = editorState,
                blocks = emptyList(),
                onPublish = { editorViewModel.publish { state -> SyncSession.publish(context, state, mode) } },
                onTitleChange = editorViewModel::updateTitle,
                onBodyChange = editorViewModel::updateBody,
                onClose = closeEditor,
                onModeChange = { editingMode = it },
                photoActions = photoActions,
            )
        }
        return
    }

    openEntryId?.let { entryId ->
        val detailViewModel = remember(entryId) {
            EntryDetailViewModel(
                loader = { id -> loadEntryDetail(database, id) },
                addComment = { id, body ->
                    val session = SyncSession.load(context)
                        ?: error("设备尚未绑定：请先完成 bootstrap 与配对")
                    com.twomemory.database.LocalEntryWriter(database).addComment(
                        coupleId = session.coupleId,
                        entryId = java.util.UUID.fromString(id),
                        commentId = java.util.UUID.randomUUID(),
                        authorId = session.userId,
                        body = body,
                    )
                    SyncSession.triggerSync(context)
                },
                currentUserId = SyncSession.load(context)?.userId,
            )
        }
        LaunchedEffect(entryId) { detailViewModel.open(entryId) }
        LaunchedEffect(cachedNames) {
            detailViewModel.updateNames(cachedNames.own, cachedNames.partner)
        }
        BackHandler { openEntryId = null }
        EntryDetailRoute(detailViewModel, onBack = { openEntryId = null })
        return
    }

    Scaffold(
        bottomBar = {
            MoonLetterBottomNavigation(selectedKey) { tab ->
                if (tab.key == "create") {
                    editorViewModel.reset()
                    editingMode = DraftStore.load(context)?.mode ?: EntryMode.PERSONAL
                } else {
                    selectedKey = tab.key
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedKey) {
                "timeline" -> TimelineRoute(
                    viewModel = timelineViewModel,
                    coverBitmap = coverBitmap,
                    onChangeCover = { coverPicker.launch(arrayOf("image/*")) },
                    onOpen = { openEntryId = it },
                )
                "couple" -> CoupleRoute(
                    viewModel = coupleViewModel,
                    onThemeChange = onThemeChange,
                    serverOwnName = cachedNames.own,
                    serverPartnerName = cachedNames.partner,
                    onSaveName = { name ->
                        val saved = SyncSession.renameOwn(context, name)
                        cachedNames = SyncSession.loadNames(context)
                        timelineViewModel.updateNames(
                            own = cachedNames.own.ifBlank { saved },
                            partner = cachedNames.partner,
                        )
                        saved
                    },
                )
                "album" -> AlbumPreviewScreen()
                "map" -> CityMapPreviewScreen()
            }
        }
    }
}
