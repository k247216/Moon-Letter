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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.twomemory.app.notifications.NotificationPreferences
import com.twomemory.app.notifications.WeeklyReviewWorker
import com.twomemory.couple.CoupleRoute
import com.twomemory.couple.CoupleToolRoute
import com.twomemory.couple.CoupleViewModel
import com.twomemory.couple.PairingCode
import com.twomemory.couple.RelationshipToolsScreen
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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

private fun occurredLabels(entry: com.twomemory.database.EntryEntity): Triple<String, String, String> {
    val zone = runCatching { ZoneId.of(entry.occurredTimezone) }.getOrElse { ZoneId.of("UTC") }
    val local = java.time.Instant.ofEpochMilli(entry.occurredAtEpochMillis).atZone(zone)
    return Triple(
        local.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
        local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)),
        local.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)),
    )
}

private fun authorLabel(
    entry: com.twomemory.database.EntryEntity,
    currentUserId: java.util.UUID?,
    names: SyncSession.Names,
) = if (currentUserId?.toString() == entry.authorId) {
    names.own.ifBlank { "我" }
} else {
    names.partner.ifBlank { "伴侣" }
}

private fun localPathOf(block: com.twomemory.database.EntryBlockEntity): String? =
    payloadOf(block).optString("localPath").takeIf { it.isNotBlank() && java.io.File(it).isFile }

private fun albumMediaOf(
    entries: List<com.twomemory.database.EntryEntity>,
    blocks: List<com.twomemory.database.EntryBlockEntity>,
    currentUserId: java.util.UUID?,
    names: SyncSession.Names,
): List<AlbumMediaUi> {
    val entryById = entries.associateBy { it.id }
    return blocks.asSequence()
        .filter { it.type == "IMAGE" || it.type == "VIDEO" }
        .mapNotNull { block ->
            val entry = entryById[block.entryId] ?: return@mapNotNull null
            if (entry.state != "PUBLISHED" || entry.deleted) return@mapNotNull null
            val (date, time, month) = occurredLabels(entry)
            AlbumMediaUi(
                id = block.id,
                entryId = block.entryId,
                monthLabel = month,
                dateLabel = date,
                timeLabel = time,
                author = authorLabel(entry, currentUserId, names),
                kind = block.type,
                localPath = localPathOf(block),
                assetId = block.assetId,
            )
        }
        .toList()
}

private fun cityStoriesOf(
    entries: List<com.twomemory.database.EntryEntity>,
    blocks: List<com.twomemory.database.EntryBlockEntity>,
    currentUserId: java.util.UUID?,
    names: SyncSession.Names,
): List<CityStoryUi> {
    val entryById = entries.associateBy { it.id }
    val blocksByEntry = blocks.groupBy { it.entryId }
    return blocks.asSequence()
        .filter { it.type == "LOCATION" }
        .mapNotNull { block ->
            val entry = entryById[block.entryId] ?: return@mapNotNull null
            // A locally saved city snapshot should be readable by its author
            // while waiting for sync, but never leak a private draft to the
            // partner's map projection.
            val ownPending = entry.authorId == currentUserId?.toString()
            if ((entry.state != "PUBLISHED" && !ownPending) || entry.deleted) return@mapNotNull null
            val payload = payloadOf(block)
            val city = payload.optString("city")
                .ifBlank { payload.optString("cityName") }
                .ifBlank { payload.optString("name") }
                .trim()
            if (city.isBlank()) return@mapNotNull null
            val (date, _, _) = occurredLabels(entry)
            CityStoryUi(
                id = block.id,
                entryId = block.entryId,
                city = city,
                dateLabel = date,
                title = entry.title?.takeIf { it.isNotBlank() },
                preview = previewOf(blocksByEntry[entry.id].orEmpty()).orEmpty(),
                author = authorLabel(entry, currentUserId, names),
            )
        }
        .toList()
}

private fun reviewMemoriesOf(
    entries: List<com.twomemory.database.EntryEntity>,
    blocks: List<com.twomemory.database.EntryBlockEntity>,
    currentUserId: java.util.UUID?,
    names: SyncSession.Names,
): List<ReviewMemoryUi> {
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val nextWeek = weekStart.plusDays(7)
    val blocksByEntry = blocks.groupBy { it.entryId }
    return entries.asSequence()
        .filter { it.state == "PUBLISHED" && !it.deleted }
        .mapNotNull { entry ->
            val zone = runCatching { ZoneId.of(entry.occurredTimezone) }.getOrElse { ZoneId.of("UTC") }
            val local = java.time.Instant.ofEpochMilli(entry.occurredAtEpochMillis).atZone(zone)
            val localDate = local.toLocalDate()
            val pastToday = localDate.year < today.year && localDate.month == today.month && localDate.dayOfMonth == today.dayOfMonth
            val inWeek = !localDate.isBefore(weekStart) && localDate.isBefore(nextWeek)
            val prefix = when {
                pastToday -> "past:"
                inWeek -> "week:"
                else -> return@mapNotNull null
            }
            val ownBlocks = blocksByEntry[entry.id].orEmpty()
            val mediaKinds = ownBlocks.mapNotNull { block ->
                when (block.type) {
                    "IMAGE" -> "照片"
                    "VIDEO" -> "视频"
                    "AUDIO" -> "语音"
                    "MUSIC" -> "音乐"
                    else -> null
                }
            }.distinct()
            val city = ownBlocks.firstOrNull { it.type == "LOCATION" }?.let { block ->
                val payload = payloadOf(block)
                payload.optString("city")
                    .ifBlank { payload.optString("cityName") }
                    .ifBlank { payload.optString("name") }
                    .takeIf { it.isNotBlank() }
            }
            val (date, time, _) = occurredLabels(entry)
            ReviewMemoryUi(
                id = prefix + entry.id,
                dateLabel = date,
                timeLabel = time,
                author = authorLabel(entry, currentUserId, names),
                title = entry.title?.takeIf { it.isNotBlank() },
                body = previewOf(ownBlocks).orEmpty(),
                mediaKinds = mediaKinds,
                city = city,
            )
        }
        .toList()
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
        payload = payload.toString(),
    )
}

private fun com.twomemory.database.CommentEntity.toEntryComment() = com.twomemory.model.EntryComment(
    id = java.util.UUID.fromString(id),
    entryId = java.util.UUID.fromString(entryId),
    authorId = runCatching { java.util.UUID.fromString(authorId) }.getOrNull(),
    body = body,
    createdAt = java.time.Instant.ofEpochMilli(createdAtEpochMillis),
)

/**
 * Unpublished editor text survives process death through SharedPreferences.
 * Every record type owns its own slot, so a half-written personal record is
 * never replaced by a half-written shared one.
 */
object DraftStore {
    private const val PREFS = "moon_letter_draft"

    data class Draft(
        val title: String,
        val body: String,
        val photos: List<com.twomemory.editor.EditorPhoto> = emptyList(),
    )

    fun save(
        context: android.content.Context,
        mode: EntryMode,
        title: String,
        body: String,
        photos: List<com.twomemory.editor.EditorPhoto> = emptyList(),
    ) {
        val editor = prefs(context).edit()
        if (title.isBlank() && body.isBlank() && photos.isEmpty()) {
            erase(editor, mode)
        } else {
            editor
                .putString(field(mode, "title"), title)
                .putString(field(mode, "body"), body)
                .putString(field(mode, "photos"), JSONArray().apply {
                    photos.forEach { photo ->
                        put(JSONObject().put("id", photo.id.toString())
                            .put("localPath", photo.localPath)
                            .put("mime", photo.mimeType))
                    }
                }.toString())
        }
        editor.apply()
    }

    /**
     * Shares are additive: an incoming note must not silently erase a draft
     * the user had already started. The editor still shows one ordinary
     * personal draft, with the shared text and photos ready to edit.
     */
    fun mergeIncomingShare(
        context: android.content.Context,
        title: String,
        body: String,
        photos: List<com.twomemory.editor.EditorPhoto>,
    ) {
        val existing = load(context, EntryMode.PERSONAL)
        val mergedTitle = existing?.title?.takeIf { it.isNotBlank() } ?: title
        val mergedBody = listOf(existing?.body.orEmpty(), body)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val mergedPhotos = (existing?.photos.orEmpty() + photos).distinctBy { it.id }
        save(context, EntryMode.PERSONAL, mergedTitle, mergedBody, mergedPhotos)
    }

    fun load(context: android.content.Context, mode: EntryMode): Draft? {
        val stored = prefs(context)
        val title = stored.getString(field(mode, "title"), null).orEmpty()
        val body = stored.getString(field(mode, "body"), null).orEmpty()
        val photos = runCatching {
            val array = JSONArray(stored.getString(field(mode, "photos"), "[]").orEmpty())
            (0 until array.length()).map { index ->
                val photo = array.getJSONObject(index)
                com.twomemory.editor.EditorPhoto(
                    id = java.util.UUID.fromString(photo.getString("id")),
                    localPath = photo.getString("localPath"),
                    mimeType = photo.optString("mime", "image/jpeg"),
                )
            }
        }.getOrDefault(emptyList())
        return if (title.isBlank() && body.isBlank() && photos.isEmpty()) null
        else Draft(title, body, photos)
    }

    fun clear(context: android.content.Context, mode: EntryMode) {
        prefs(context).edit().also { erase(it, mode) }.apply()
    }

    private fun prefs(context: android.content.Context) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    private fun field(mode: EntryMode, name: String) = "${mode.name}.$name"

    private fun erase(editor: android.content.SharedPreferences.Editor, mode: EntryMode) {
        editor.remove(field(mode, "title"))
            .remove(field(mode, "body"))
            .remove(field(mode, "photos"))
    }
}

@Composable
fun AppNavigation(
    onThemeChange: (MoonLetterTheme) -> Unit = {},
    initialEntryId: String? = null,
    initialEditorMode: String? = null,
    initialEditorRequest: Int = 0,
) {
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
    var toolRoute by remember { mutableStateOf<CoupleToolRoute?>(null) }
    var reviewRoute by remember { mutableStateOf<MemoryReviewRoute?>(null) }
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
    val roomEntries by database.entryDao().observeTimeline().collectAsState(initial = emptyList())
    val roomBlocks by database.entryDao().observeBlocks().collectAsState(initial = emptyList())
    val timelineViewModel = remember {
        val dao = database.entryDao()
        TimelineViewModel(
            observer = {
                combine(dao.observeTimeline(), dao.observeBlocks()) { entries, blocks ->
                    entries.map { entity ->
                        val own = blocks.filter { it.entryId == entity.id }
                        entity.toTimelineItem(previewOf(own), photoOf(own))
                    }
                }
            },
            currentUserId = SyncSession.load(context)?.userId,
        )
    }
    var openEntryId by remember { mutableStateOf<String?>(null) }

    // A tapped notice opens exactly one record — and only when it is still here,
    // so a record deleted since then cannot produce an empty page.
    LaunchedEffect(initialEntryId) {
        val entryId = initialEntryId ?: return@LaunchedEffect
        if (database.entryDao().findEntry(entryId) != null) openEntryId = entryId
    }

    val notificationPrefs = remember { NotificationPreferences(context) }
    var weeklyReviewEnabled by remember { mutableStateOf(notificationPrefs.weeklyReviewEnabled) }
    var newEntryNoticeEnabled by remember { mutableStateOf(notificationPrefs.newEntryNoticeEnabled) }
    var reviewDayOfWeek by remember { mutableStateOf(notificationPrefs.reviewDayOfWeek) }
    var reviewHour by remember { mutableStateOf(notificationPrefs.reviewHour) }
    val editorViewModel = remember { EditorViewModel() }
    val editorState by editorViewModel.state.collectAsState()
    val toolScope = rememberCoroutineScope()
    var citySnapshotStatus by remember { mutableStateOf<String?>(null) }

    // Drafts are written while the user pauses, not only when the close icon
    // is pressed. A killed process therefore loses at most the current debounce
    // window, and the editor never needs a second “save draft” step.
    LaunchedEffect(editingMode, editorState.title, editorState.body, editorState.photos, editorState.saved) {
        val mode = editingMode ?: return@LaunchedEffect
        if (editorState.saved) return@LaunchedEffect
        delay(300)
        DraftStore.save(context, mode, editorState.title, editorState.body, editorState.photos)
    }

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
    val albumMedia = remember(roomEntries, roomBlocks, cachedNames) {
        albumMediaOf(
            entries = roomEntries,
            blocks = roomBlocks,
            currentUserId = SyncSession.load(context)?.userId,
            names = cachedNames,
        )
    }
    val cityStories = remember(roomEntries, roomBlocks, cachedNames) {
        cityStoriesOf(
            entries = roomEntries,
            blocks = roomBlocks,
            currentUserId = SyncSession.load(context)?.userId,
            names = cachedNames,
        )
    }
    val reviewMemories = remember(roomEntries, roomBlocks, cachedNames) {
        reviewMemoriesOf(
            entries = roomEntries,
            blocks = roomBlocks,
            currentUserId = SyncSession.load(context)?.userId,
            names = cachedNames,
        )
    }

    // Unpublished text is kept off the critical path: restore on entry, keep
    // on every exit except a successful save.
    LaunchedEffect(editorState.saved) {
        if (editorState.saved) {
            editingMode?.let { DraftStore.clear(context, it) }
        }
    }

    /** Opens one record type with its own unsaved draft already in place. */
    val openEditor: (EntryMode) -> Unit = { mode ->
        editorViewModel.reset()
        DraftStore.load(context, mode)?.let {
            editorViewModel.restore(it.title, it.body, it.photos)
        }
        editingMode = mode
    }

    // Android Sharesheet hand-offs land in the same editor as the +记录 tab.
    // The request counter lets a second share reopen the editor while the app
    // is already visible; the mode remains explicit for future shared drafts.
    LaunchedEffect(initialEditorRequest) {
        if (initialEditorRequest > 0) {
            val mode = runCatching { EntryMode.valueOf(initialEditorMode.orEmpty()) }
                .getOrDefault(EntryMode.PERSONAL)
            openEditor(mode)
        }
    }

    editingMode?.let { mode ->
        val keepDraft = {
            DraftStore.save(context, mode, editorState.title, editorState.body, editorState.photos)
        }
        val closeEditor = {
            if (editorState.saved) DraftStore.clear(context, mode) else keepDraft()
            editingMode = null
        }
        val switchMode: (EntryMode) -> Unit = { next ->
            if (next != mode) {
                keepDraft()
                openEditor(next)
            }
        }
        BackHandler { closeEditor() }
        val photoActions = rememberEditorPhotoActions(editorViewModel)
        if (mode == EntryMode.PERSONAL) {
            PersonalEditorScreen(
                state = editorState,
                onTitleChange = editorViewModel::updateTitle,
                onBodyChange = editorViewModel::updateBody,
                onPublish = { editorViewModel.publish { state -> SyncSession.publish(context, state, mode) } },
                onClose = closeEditor,
                mode = mode,
                onModeChange = switchMode,
                photoActions = photoActions,
                ownName = cachedNames.own,
            )
        } else {
            SharedEditorRoute(
                state = editorState,
                onPublish = { editorViewModel.publish { state -> SyncSession.publish(context, state, mode) } },
                onTitleChange = editorViewModel::updateTitle,
                onBodyChange = editorViewModel::updateBody,
                onClose = closeEditor,
                onModeChange = switchMode,
                photoActions = photoActions,
                ownName = cachedNames.own,
            )
        }
        return
    }

    openEntryId?.let { entryId ->
        val detailViewModel = remember(entryId) {
            val entryDao = database.entryDao()
            val commentDao = database.commentDao()
            EntryDetailViewModel(
                observer = {
                    combine(
                        entryDao.observeEntry(entryId),
                        entryDao.observeBlocks(entryId),
                        commentDao.observeForEntry(entryId),
                    ) { entries, blocks, comments ->
                        entries.firstOrNull()?.let { entity ->
                            com.twomemory.model.EntryDetail(
                                entry = entity.toTimelineItem(previewOf(blocks), photoOf(blocks)),
                                blocks = blocks.map { it.toEntryBlock() },
                                comments = comments.map { it.toEntryComment() },
                            )
                        }
                    }
                },
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
        LaunchedEffect(cachedNames) {
            detailViewModel.updateNames(cachedNames.own, cachedNames.partner)
        }
        BackHandler { openEntryId = null }
        EntryDetailRoute(detailViewModel, onBack = { openEntryId = null })
        return
    }

    toolRoute?.let { route ->
        BackHandler { toolRoute = null }
        val toolsPrefs = context.getSharedPreferences("moon_letter_tools", android.content.Context.MODE_PRIVATE)
        RelationshipToolsScreen(
            route = route,
            onBack = { toolRoute = null },
            onSaveAnniversary = { draft ->
                context.getSharedPreferences("moon_letter_tools", android.content.Context.MODE_PRIVATE).edit()
                    .putString("anniversaryName", draft.name)
                    .putString("anniversaryDate", draft.date)
                    .putBoolean("anniversaryRepeats", draft.repeatsYearly)
                    .apply()
            },
            onSaveCapsule = { draft ->
                // Keep only lock metadata locally. The plaintext and unlock
                // enforcement belong to the server; never put the letter into
                // ordinary preferences or the local-cache export.
                context.getSharedPreferences("moon_letter_tools", android.content.Context.MODE_PRIVATE).edit()
                    .putString("capsuleTitle", draft.title)
                    .putString("capsuleUnlockDate", draft.unlockDate)
                    .putBoolean("capsuleLocked", true)
                    .apply()
            },
            onStartExport = { scope ->
                if (scope == com.twomemory.couple.ExportScope.LOCAL_CACHE) {
                    toolScope.launch {
                        val session = SyncSession.load(context)
                        val names = SyncSession.loadNames(context)
                        val document = LocalExportBuilder.build(
                            database = database,
                            currentUserId = session?.userId,
                            ownName = names.own,
                            partnerName = names.partner,
                        )
                        val text = document.markdown + "\n\n---\n\n# 机器可读 JSON\n\n" + document.json
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "月笺本机缓存导出（Markdown + JSON）")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(share, "分享月笺导出"))
                    }
                }
            },
            initialAnniversaryName = toolsPrefs.getString("anniversaryName", "我们的中秋").orEmpty(),
            initialAnniversaryDate = toolsPrefs.getString("anniversaryDate", "农历八月十五").orEmpty(),
            initialAnniversaryRepeats = toolsPrefs.getBoolean("anniversaryRepeats", true),
            initialCapsuleTitle = toolsPrefs.getString("capsuleTitle", "").orEmpty(),
            initialCapsuleUnlockDate = toolsPrefs.getString("capsuleUnlockDate", "").orEmpty(),
            initialCapsuleLocked = toolsPrefs.getBoolean("capsuleLocked", false),
        )
        return
    }

    reviewRoute?.let { route ->
        BackHandler { reviewRoute = null }
        MemoryReviewScreen(
            route = route,
            memories = reviewMemories,
            onBack = { reviewRoute = null },
            onOpenEntry = { openEntryId = it },
        )
        return
    }

    Scaffold(
        bottomBar = {
            MoonLetterBottomNavigation(selectedKey) { tab ->
                if (tab.key == "create") {
                    openEditor(
                        EntryMode.entries.firstOrNull { DraftStore.load(context, it) != null }
                            ?: EntryMode.PERSONAL,
                    )
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
                    versionLabel = "M1 · ${BuildConfig.VERSION_NAME}",
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
                    onGenerateCode = {
                        val issued = SyncSession.mintPairingCode(context)
                        PairingCode(issued.pairingTokenKind, issued.pairingToken)
                    },
                    weeklyReviewEnabled = weeklyReviewEnabled,
                    newEntryNoticeEnabled = newEntryNoticeEnabled,
                    reviewDayOfWeek = reviewDayOfWeek,
                    reviewHour = reviewHour,
                    onWeeklyReviewChange = { enabled ->
                        notificationPrefs.weeklyReviewEnabled = enabled
                        weeklyReviewEnabled = enabled
                        if (enabled) {
                            WeeklyReviewWorker.schedule(context, force = true)
                        } else {
                            WeeklyReviewWorker.cancel(context)
                        }
                    },
                    onNewEntryNoticeChange = { enabled ->
                        // Off also drops the one record id the notice remembered, so
                        // switching it back on never announces what passed while off.
                        notificationPrefs.newEntryNoticeEnabled = enabled
                        newEntryNoticeEnabled = enabled
                    },
                    onReviewTimeChange = { day, hour ->
                        notificationPrefs.reviewDayOfWeek = day
                        notificationPrefs.reviewHour = hour
                        reviewDayOfWeek = day
                        reviewHour = hour
                        WeeklyReviewWorker.schedule(context, force = true)
                    },
                    onOpenAnniversary = { toolRoute = CoupleToolRoute.ANNIVERSARY },
                    onOpenCapsule = { toolRoute = CoupleToolRoute.CAPSULE },
                    onOpenExport = { toolRoute = CoupleToolRoute.EXPORT },
                    onOpenPastToday = { reviewRoute = MemoryReviewRoute.PAST_TODAY },
                    onOpenWeeklySummary = { reviewRoute = MemoryReviewRoute.WEEKLY_SUMMARY },
                )
                "album" -> AlbumPreviewScreen(media = albumMedia, onOpenEntry = { openEntryId = it })
                "map" -> CityMapPreviewScreen(
                    stories = cityStories,
                    onOpenEntry = { openEntryId = it },
                    snapshotStatus = citySnapshotStatus,
                    onSaveCitySnapshot = { city ->
                        val session = SyncSession.load(context)
                        if (session == null) {
                            citySnapshotStatus = "设备尚未绑定，城市快照没有保存"
                        } else {
                            citySnapshotStatus = "正在保存到本机…"
                            toolScope.launch {
                                runCatching {
                                    val now = java.time.Instant.now()
                                    com.twomemory.database.LocalEntryWriter(database).save(
                                        com.twomemory.model.LocalEntryCommand(
                                            coupleId = session.coupleId,
                                            authorId = session.userId,
                                            mode = EntryMode.PERSONAL,
                                            occurredAt = now,
                                            occurredTimezone = ZoneId.systemDefault().id,
                                            title = city,
                                            blocks = listOf(
                                                com.twomemory.model.LocalBlockCommand(
                                                    type = com.twomemory.model.BlockType.LOCATION,
                                                    orderKey = 0,
                                                    payload = JSONObject()
                                                        .put("city", city)
                                                        .put("capturedAt", now.toString())
                                                        .toString(),
                                                    authorId = session.userId,
                                                ),
                                            ),
                                        ),
                                        publish = true,
                                    )
                                    SyncSession.triggerSync(context)
                                }.onSuccess {
                                    citySnapshotStatus = "已保存到本机 · 等待同步"
                                }.onFailure {
                                    citySnapshotStatus = "城市快照保存失败，请重试"
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
