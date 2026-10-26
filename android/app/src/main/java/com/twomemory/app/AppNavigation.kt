package com.twomemory.app

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.editor.EditorViewModel
import com.twomemory.editor.PersonalEditorScreen
import com.twomemory.editor.SharedEditorRoute
import com.twomemory.editor.rememberEditorAttachmentActions
import com.twomemory.editor.rememberEditorPhotoActions
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryState
import com.twomemory.model.EntrySyncPhase
import com.twomemory.model.BlockType
import com.twomemory.model.TimelineAttachment
import com.twomemory.model.TimelineItem
import com.twomemory.timeline.EntryDetailRoute
import com.twomemory.timeline.EntryDetailViewModel
import com.twomemory.timeline.TimelineRoute
import com.twomemory.timeline.TimelineViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal fun com.twomemory.database.EntryEntity.toTimelineItem(
    preview: String?,
    photo: com.twomemory.model.TimelinePhoto?,
    attachments: List<TimelineAttachment> = emptyList(),
    notePaper: com.twomemory.model.NotePaperAppearance = com.twomemory.model.NotePaperAppearance(),
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
    attachments = attachments,
    notePaper = notePaper,
    visibility = com.twomemory.model.EntryVisibility.valueOf(visibility),
)

private fun payloadOf(block: com.twomemory.database.EntryBlockEntity) =
    runCatching { JSONObject(block.payload) }.getOrElse { JSONObject() }

internal fun previewOf(blocks: List<com.twomemory.database.EntryBlockEntity>): String? =
    blocks.firstOrNull { it.type == "TEXT" }?.let { block ->
        payloadOf(block).optString("text").takeIf { it.isNotBlank() } ?: block.payload
    }

internal fun photoOf(blocks: List<com.twomemory.database.EntryBlockEntity>) =
    blocks.firstOrNull { it.type == "IMAGE" && (it.assetId != null || it.entryPath != null) }?.let { block ->
        com.twomemory.model.TimelinePhoto(block.entryPath, block.assetId)
    }

internal fun attachmentsOf(blocks: List<com.twomemory.database.EntryBlockEntity>): List<TimelineAttachment> =
    blocks.asSequence()
        .filter { it.type == "LOCATION" || it.type == "MUSIC" || it.type == "VIDEO" || it.type == "AUDIO" }
        .mapNotNull { block ->
            val payload = payloadOf(block)
            val type = runCatching { BlockType.valueOf(block.type) }.getOrNull() ?: return@mapNotNull null
            val title = when (type) {
                BlockType.LOCATION -> payload.optString("city")
                    .ifBlank { payload.optString("cityName") }
                    .ifBlank { payload.optString("name") }
                    .ifBlank { "地点快照" }
                BlockType.MUSIC -> payload.optString("title")
                    .ifBlank { payload.optString("track") }
                    .ifBlank { payload.optString("name") }
                    .ifBlank { "音乐分享" }
                BlockType.VIDEO -> "视频片段"
                BlockType.AUDIO -> payload.optString("title").ifBlank { "语音片段" }
                else -> return@mapNotNull null
            }
            val detail = when (type) {
                BlockType.LOCATION -> payload.optString("note")
                    .ifBlank { payload.optString("address") }
                    .ifBlank { "把这一刻留在地图上" }
                BlockType.MUSIC -> payload.optString("artist")
                    .ifBlank { payload.optString("album") }
                    .ifBlank { "一首歌，也是一句没有说完的话" }
                BlockType.VIDEO -> "视频记忆"
                BlockType.AUDIO -> "声音记忆"
                else -> null
            }
            TimelineAttachment(type, title, detail,
                url = payload.optString("url").ifBlank { payload.optString("link") }.takeIf(String::isNotBlank),
                localPath = localPathOf(block), assetId = block.assetId)
        }
        .toList()

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
    names.partner.ifBlank { "对方" }
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
    currentCoupleId: java.util.UUID?,
    names: SyncSession.Names,
): List<CityStoryUi> {
    val entryById = entries.associateBy { it.id }
    val blocksByEntry = blocks.groupBy { it.entryId }
    return blocks.asSequence()
        .filter { it.type == "LOCATION" && !it.deleted }
        .mapNotNull { block ->
            val entry = entryById[block.entryId] ?: return@mapNotNull null
            if (entry.coupleId != currentCoupleId?.toString()) return@mapNotNull null
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
                photoLocalPath = blocksByEntry[entry.id].orEmpty().firstOrNull { it.type == "IMAGE" && !it.deleted }
                    ?.let { payloadOf(it).optString("localPath").takeIf(String::isNotBlank) },
                photoAssetId = blocksByEntry[entry.id].orEmpty().firstOrNull { it.type == "IMAGE" && !it.deleted }?.assetId,
                timeLabel = occurredLabels(entry).second,
                occurredAtEpochMillis = entry.occurredAtEpochMillis,
                mine = entry.authorId == currentUserId?.toString(),
                shared = entry.mode == EntryMode.COLLABORATIVE.name,
                attachments = attachmentsOf(blocksByEntry[entry.id].orEmpty()).filter { it.type != BlockType.LOCATION }
                    .map { com.twomemory.timeline.TimelineAttachmentUi(it.type, it.title, it.detail, it.url, it.localPath, it.assetId) },
            )
        }
        .distinctBy { it.entryId to (com.twomemory.model.CityCatalog.find(it.city)?.name ?: it.city) }
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
        authorId = runCatching { java.util.UUID.fromString(authorId) }.getOrNull(),
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
 * What this phone's own queue says about one record, in the words its badge uses.
 * Kept separate from the screens so the contract is testable without a device.
 */
internal fun recordStatusOf(phase: EntrySyncPhase): MoonLetterRecordStatus = when (phase) {
    EntrySyncPhase.DELIVERED -> MoonLetterRecordStatus.SYNCED
    EntrySyncPhase.QUEUED -> MoonLetterRecordStatus.PENDING_SYNC
    EntrySyncPhase.UPLOADING_MEDIA -> MoonLetterRecordStatus.UPLOADING_MEDIA
    EntrySyncPhase.RETRYING -> MoonLetterRecordStatus.RETRYING
    EntrySyncPhase.REJECTED -> MoonLetterRecordStatus.REJECTED
}

/**
 * Live badge for a record this device wrote. Null while nothing has been published
 * here, so a page keeps the badge it derived from the record's own state rather
 * than claiming delivery the queue has not confirmed.
 */
@Composable
private fun rememberDeliveryStatus(entryId: java.util.UUID?): MoonLetterRecordStatus? {
    val context = LocalContext.current
    val phase by remember(entryId) {
        if (entryId == null) flowOf<EntrySyncPhase?>(null) else SyncSession.observeSyncPhase(context, entryId)
    }.collectAsState(initial = null)
    return phase?.let(::recordStatusOf)
}

/**
 * A share sheet carries its text through a single binder transaction, so an
 * export longer than this is reported on screen instead of crashing the chooser.
 */
private const val MAX_SHARE_CHARS = 200_000

/** Turns a day the couple typed into the instant the export endpoint expects. */
private fun exportInstant(day: String, endOfDay: Boolean): String? {
    val date = runCatching { java.time.LocalDate.parse(day.trim()) }.getOrNull() ?: return null
    val atTime = date.atTime(
        if (endOfDay) java.time.LocalTime.MAX else java.time.LocalTime.MIN,
    )
    return atTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toString()
}

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
        val attachments: List<com.twomemory.editor.EditorAttachment> = emptyList(),
        /** A moment the writer picked. Without one, a resumed draft takes the current time. */
        val occurrence: java.time.Instant? = null,
        val visibility: com.twomemory.model.EntryVisibility = com.twomemory.model.EntryVisibility.SHARED,
    )

    fun save(
        context: android.content.Context,
        mode: EntryMode,
        title: String,
        body: String,
        photos: List<com.twomemory.editor.EditorPhoto> = emptyList(),
        attachments: List<com.twomemory.editor.EditorAttachment> = emptyList(),
        occurrence: java.time.Instant? = null,
        visibility: com.twomemory.model.EntryVisibility = com.twomemory.model.EntryVisibility.SHARED,
    ) {
        val editor = prefs(context).edit()
        if (title.isBlank() && body.isBlank() && photos.isEmpty() && attachments.isEmpty()) {
            erase(editor, mode)
        } else {
            editor
                .putString(field(mode, "title"), title)
                .putString(field(mode, "body"), body)
                .putString(field(mode, "visibility"), visibility.name)
                .putString(field(mode, "photos"), JSONArray().apply {
                    photos.forEach { photo ->
                        put(JSONObject().put("id", photo.id.toString())
                            .put("localPath", photo.localPath)
                            .put("mime", photo.mimeType))
                    }
                }.toString())
                .putString(field(mode, "attachments"), JSONArray().apply {
                    attachments.forEach { attachment ->
                        put(JSONObject().put("id", attachment.id.toString())
                            .put("type", attachment.type.name)
                            .put("localPath", attachment.localPath ?: JSONObject.NULL)
                            .put("mime", attachment.mimeType ?: JSONObject.NULL)
                            .put("payload", attachment.payload))
                    }
                }.toString())
            if (occurrence == null) editor.remove(field(mode, "occurrenceAt"))
            else editor.putLong(field(mode, "occurrenceAt"), occurrence.toEpochMilli())
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
        attachments: List<com.twomemory.editor.EditorAttachment> = emptyList(),
    ) {
        val existing = load(context, EntryMode.PERSONAL)
        val mergedTitle = existing?.title?.takeIf { it.isNotBlank() } ?: title
        val mergedBody = listOf(existing?.body.orEmpty(), body)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val mergedPhotos = (existing?.photos.orEmpty() + photos).distinctBy { it.id }
        save(
            context,
            EntryMode.PERSONAL,
            mergedTitle,
            mergedBody,
            mergedPhotos,
            attachments = (existing?.attachments.orEmpty() + attachments).distinctBy { it.id },
            occurrence = existing?.occurrence,
            visibility = existing?.visibility ?: com.twomemory.model.EntryVisibility.SHARED,
        )
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
        val attachments = runCatching {
            val array = JSONArray(stored.getString(field(mode, "attachments"), "[]").orEmpty())
            (0 until array.length()).map { index ->
                val attachment = array.getJSONObject(index)
                com.twomemory.editor.EditorAttachment(
                    id = java.util.UUID.fromString(attachment.getString("id")),
                    type = com.twomemory.model.BlockType.valueOf(attachment.getString("type")),
                    localPath = attachment.optString("localPath").takeIf { it.isNotBlank() && it != "null" },
                    mimeType = attachment.optString("mime").takeIf { it.isNotBlank() && it != "null" },
                    payload = attachment.optString("payload", "{}"),
                )
            }
        }.getOrDefault(emptyList())
        val occurrence = if (stored.contains(field(mode, "occurrenceAt")))
            java.time.Instant.ofEpochMilli(stored.getLong(field(mode, "occurrenceAt"), 0L))
        else null
        return if (title.isBlank() && body.isBlank() && photos.isEmpty() && attachments.isEmpty()) null
        else Draft(title, body, photos, attachments, occurrence,
            runCatching { com.twomemory.model.EntryVisibility.valueOf(stored.getString(field(mode, "visibility"), "SHARED")!!) }.getOrDefault(com.twomemory.model.EntryVisibility.SHARED))
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
            .remove(field(mode, "attachments"))
            .remove(field(mode, "occurrenceAt"))
            .remove(field(mode, "visibility"))
    }
}

@Composable
fun AppNavigation(
    onThemeChange: (MoonLetterTheme) -> Unit = {},
    initialEntryId: String? = null,
    initialEditorMode: String? = null,
    initialEditorRequest: Int = 0,
    onInitialContentReady: () -> Unit = {},
    initialReminder: String? = null,
    initialNoticeScope: String? = null,
    initialNoticeRequest: Int = 0,
) {
    val context = LocalContext.current
    var bound by remember { mutableStateOf(SyncSession.load(context) != null) }
    if (!bindingStorageAvailable()) {
        LaunchedEffect(Unit) { onInitialContentReady() }
        return
    }
    if (!bound && SyncSession.load(context) == null) {
        LaunchedEffect(Unit) { onInitialContentReady() }
        SetupScreen(onBound = { bound = true })
        return
    }
    var selectedKey by remember { mutableStateOf("timeline") }
    val recordingSession = SyncSession.load(context) ?: return
    val recordingScope = "${recordingSession.storageOrigin}|${recordingSession.coupleId}|${recordingSession.userId}"
    // Own the reading position above detail/editor early returns. It belongs to
    // this binding, not the whole installation and not a particular visible tab.
    val timelineListState = key(recordingScope) { rememberLazyListState() }
    val anniversaryBinding = rememberLiveBinding(context) ?: return
    val independentAnniversary = remember(anniversaryBinding) { IndependentAnniversaryStore.store(context, anniversaryBinding) }
    val independentAnniversaryState by independentAnniversary.state.collectAsState()
    DisposableEffect(independentAnniversary) {
        val observation = independentAnniversary.observeConfirmed()
        onDispose { observation.close() }
    }
    var occasionsOpen by remember(recordingScope) { mutableStateOf(false) }
    val occasionStore = remember(recordingScope) { OccasionCloud.store(context, recordingSession) }
    var occasionCards by remember(recordingScope) { mutableStateOf(occasionStore.cards()) }
    var occasionDismissRevision by remember(recordingScope) { mutableStateOf(0) }
    var occasionToday by remember { mutableStateOf(LocalDate.now()) }
    DisposableEffect(occasionStore) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> occasionCards = occasionStore.cards() }
        occasionStore.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { occasionStore.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(recordingScope) {
        OccasionCloud.refresh(context)
        CapsuleCloud.refresh(context)
        com.twomemory.app.notifications.CloudKeepsakeReminders.schedule(context)
        while (true) { kotlinx.coroutines.delay(30_000); occasionToday = LocalDate.now() }
    }
    val draftLibrary = remember(recordingScope) { DraftLibrary(context, recordingScope).also { it.importLegacyDrafts() } }
    val readingProgress = remember(recordingScope) { ReadingProgressStore(context, recordingScope) }
    val bookmarkStore = remember(recordingScope) { MemoryBookmarkStore(context, recordingSession.storageOrigin,
        recordingSession.coupleId.toString(), recordingSession.userId.toString()) }
    var bookmarkIds by remember(recordingScope) { mutableStateOf(bookmarkStore.ids()) }
    var bookmarksOpen by remember(recordingScope) { mutableStateOf(false) }
    var bookmarkError by remember(recordingScope) { mutableStateOf<String?>(null) }
    var readIds by remember(recordingScope) { mutableStateOf(readingProgress.readIds()) }
    var draftBoxOpen by remember { mutableStateOf(false) }
    var activeDraftId by remember { mutableStateOf<String?>(null) }
    var savedDrafts by remember(recordingScope) { mutableStateOf(draftLibrary.list()) }
    var editingMode by remember { mutableStateOf<EntryMode?>(null) }
    var littleNoteOpen by rememberSaveable(recordingScope) { mutableStateOf(false) }
    var perspectiveEntryId by remember { mutableStateOf<java.util.UUID?>(null) }
    val draftContext = remember(context, perspectiveEntryId, recordingScope) {
        perspectiveEntryId?.let { perspectiveDraftContext(context, it) } ?: context
    }
    /** The record this session published; its queue state is what the editor then shows. */
    var publishedEntryId by remember { mutableStateOf<java.util.UUID?>(null) }
    var toolRoute by rememberSaveable { mutableStateOf<CoupleToolRoute?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var reviewRoute by remember { mutableStateOf<MemoryReviewRoute?>(null) }
    val reviewStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var openEntryId by remember { mutableStateOf<String?>(null) }
    var timelineCoverVisible by remember { mutableStateOf(true) }
    val statusWindowView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(selectedKey, timelineCoverVisible, editingMode, openEntryId, toolRoute, reviewRoute) {
        (statusWindowView.context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, statusWindowView).isAppearanceLightStatusBars =
                selectedKey != "timeline" || !timelineCoverVisible || editingMode != null ||
                    openEntryId != null || toolRoute != null || reviewRoute != null
        }
        onDispose { }
    }
    val visualPrefs = remember { context.getSharedPreferences("moon_letter_visuals", android.content.Context.MODE_PRIVATE) }
    val toolsPrefs = remember { context.getSharedPreferences("moon_letter_tools", android.content.Context.MODE_PRIVATE) }
    var anniversaryName by remember { mutableStateOf(toolsPrefs.getString("anniversaryName", "").orEmpty()) }
    var anniversaryDate by remember { mutableStateOf(toolsPrefs.getString("anniversaryDate", "").orEmpty()) }
    var anniversaryRepeats by remember { mutableStateOf(toolsPrefs.getBoolean("anniversaryRepeats", true)) }
    var anniversaryShared by remember { mutableStateOf(false) }
    DisposableEffect(toolsPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            anniversaryName = toolsPrefs.getString("anniversaryName", "").orEmpty()
            anniversaryDate = toolsPrefs.getString("anniversaryDate", "").orEmpty()
            anniversaryRepeats = toolsPrefs.getBoolean("anniversaryRepeats", true)
        }
        toolsPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { toolsPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
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
    val coverPixels = rememberDecorBitmap(coverUri, 1280, recordingScope)
    val coverBitmap = coverPixels.bitmap
    // The profile page stores the face it picked; nothing else reads it back, so the
    // avatar would only ever change the one page it was chosen on.
    val profilePrefs = remember {
        context.getSharedPreferences("moon_letter_profile", android.content.Context.MODE_PRIVATE)
    }
    var ownAvatarUri by remember { mutableStateOf(profilePrefs.getString("ownAvatar", null)) }
    var partnerAvatarUri by remember { mutableStateOf(profilePrefs.getString("partnerAvatar", null)) }
    DisposableEffect(profilePrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            ownAvatarUri = profilePrefs.getString("ownAvatar", null)
            partnerAvatarUri = profilePrefs.getString("partnerAvatar", null)
        }
        profilePrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { profilePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val partnerPixels = rememberDecorBitmap(partnerAvatarUri, 192, recordingScope)
    val partnerAvatarBitmap = partnerPixels.bitmap
    LaunchedEffect(selectedKey) { ownAvatarUri = profilePrefs.getString("ownAvatar", null) }
    val ownPixels = rememberDecorBitmap(ownAvatarUri, 192, recordingScope)
    val ownAvatarBitmap = ownPixels.bitmap
    val coupleViewModel = remember { CoupleViewModel() }
    var cachedNames by remember { mutableStateOf(SyncSession.loadNames(context)) }
    var cityScope by remember { mutableStateOf(SyncSession.load(context)?.let { it.coupleId to it.userId }) }
    // Her rename lands in these two keys at the end of a sync cycle, and every page
    // reads this cache — so the cache has to wake the pages instead of waiting for
    // the next launch. Remembered: SharedPreferences holds its listeners weakly.
    val namesListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "coupleId" || key == "userId") {
                cityScope = SyncSession.load(context)?.let { it.coupleId to it.userId }
            }
            if (key == null || key == "ownName" || key == "partnerName" || key.startsWith("coralSigner_")) {
                cachedNames = SyncSession.loadNames(context)
            }
        }
    }
    DisposableEffect(namesListener) {
        val prefs = SyncSession.sessionPrefs(context)
        prefs.registerOnSharedPreferenceChangeListener(namesListener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(namesListener) }
    }
    val database = remember { AppDatabase.build(context) }
    val roomEntries by database.entryDao().observeTimeline().collectAsState(initial = emptyList())
    val roomBlocks by database.entryDao().observeBlocks().collectAsState(initial = emptyList())
    val timelineViewModel = remember {
        val dao = database.entryDao()
        TimelineViewModel(
            observer = {
                combine(dao.observeTimeline().distinctUntilChanged(), dao.observeBlocks().distinctUntilChanged()) { entries, blocks ->
                    projectTimelineRows(entries,blocks)
                }
            },
            currentUserId = SyncSession.load(context)?.userId,
        )
    }
    val firstTimelineLoading by timelineViewModel.initialLoading.collectAsState()
    val bookmarkEntries by timelineViewModel.entries.collectAsState()
    LaunchedEffect(firstTimelineLoading, coverPixels.ready, ownPixels.ready, partnerPixels.ready,
        initialEntryId, initialEditorMode) {
        if (initialEntryId != null || initialEditorMode != null ||
            (!firstTimelineLoading && coverPixels.ready && ownPixels.ready && partnerPixels.ready))
            onInitialContentReady()
    }

    // A tapped notice opens exactly one record — and only when it is still here,
    // so a record deleted since then cannot produce an empty page.
    var handledNotice by remember(recordingScope) { mutableStateOf<String?>(null) }
    LaunchedEffect(initialEntryId, initialReminder, initialNoticeRequest, recordingScope, editingMode, littleNoteOpen) {
        val request = "$initialNoticeRequest|$initialEntryId|$initialReminder"
        if (request == handledNotice) return@LaunchedEffect
        // A notice must not dispose an in-progress editor or cross account boundaries.
        if (editingMode != null || littleNoteOpen || (initialNoticeScope != null &&
            initialNoticeScope != com.twomemory.app.notifications.NoticeTargetScope.of(recordingSession))) return@LaunchedEffect
        val entryId = initialEntryId
        if (entryId != null) {
            val entry = database.entryDao().findEntry(entryId) ?: return@LaunchedEffect
            if (entry.deleted || entry.state != "PUBLISHED" || entry.coupleId != recordingSession.coupleId.toString() ||
                (entry.visibility != "SHARED" && entry.authorId != recordingSession.userId.toString())) return@LaunchedEffect
            toolRoute = null
            reviewRoute = null
            occasionsOpen = false
            openEntryId = entryId
            handledNotice = request
        } else if (initialReminder != null) {
            openEntryId = null
            reviewRoute = null
            occasionsOpen = initialReminder == "occasion"
            toolRoute = when (initialReminder) {
                "anniversary" -> CoupleToolRoute.ANNIVERSARY
                "capsule" -> CoupleToolRoute.CAPSULE
                else -> null
            }
            handledNotice = request
        }
    }

    val notificationPrefs = remember { NotificationPreferences(context) }
    var weeklyReviewEnabled by remember { mutableStateOf(notificationPrefs.weeklyReviewEnabled) }
    var newEntryNoticeEnabled by remember { mutableStateOf(notificationPrefs.newEntryNoticeEnabled) }
    var reviewDayOfWeek by remember { mutableStateOf(notificationPrefs.reviewDayOfWeek) }
    var reviewHour by remember { mutableStateOf(notificationPrefs.reviewHour) }
    val editorViewModel = remember { EditorViewModel() }
    val editorState by editorViewModel.state.collectAsState()
    val latestEditorState by rememberUpdatedState(editorState)
    val toolScope = rememberCoroutineScope()
    var pendingMemoryBookName by rememberSaveable(recordingScope) { mutableStateOf<String?>(null) }
    // A saved busy flag cannot resurrect a coroutine after process death.
    // Only an existing file awaiting the save picker keeps the restored UI busy.
    var memoryBookBusy by remember(recordingScope) { mutableStateOf(pendingBookFile(context, pendingMemoryBookName) != null) }
    val bookSavePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { destination ->
        val bookFile = pendingBookFile(context, pendingMemoryBookName)
        pendingMemoryBookName = null
        if (destination == null || bookFile == null) {
            bookFile?.delete()
            memoryBookBusy = false
            exportStatus = "已取消保存纪念册"
        } else toolScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val expected = bookFile.length()
                    val copied = bookFile.inputStream().use { input ->
                        val output = context.contentResolver.openOutputStream(destination, "wt") ?: error("无法创建纪念册文件")
                        output.use { stream ->
                            val buffer = ByteArray(32 * 1024)
                            var bytes = 0L
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val n = input.read(buffer); if (n < 0) break
                                stream.write(buffer, 0, n); bytes += n
                            }
                            bytes
                        }
                    }
                    check(copied == expected && copied > 0) { "纪念册没有完整写入" }
                }
                exportStatus = "导出文件已保存。手书请完整解压后打开 index.html；数据存档请保留 archive 与 media 文件夹。"
            } catch (failure: Exception) {
                runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, destination) }
                exportStatus = "纪念册保存没完成：${failure.message.orEmpty()}"
            } finally {
                bookFile.delete()
                memoryBookBusy = false
            }
        }
    }

    LaunchedEffect(toolRoute, independentAnniversary) {
        if (toolRoute == CoupleToolRoute.ANNIVERSARY) independentAnniversary.refresh()
    }

    // Real display names win over the placeholder couple state: the space is
    // read once when the app is entered, and again after a rename succeeds.
    // Typing in the name field must not reach the network.
    LaunchedEffect(Unit) {
        SyncSession.load(context)?.let { SyncSession.installPhotoSource(context, it) }
        SyncSession.refreshNames(context)
        cachedNames = SyncSession.loadNames(context)
    }
    LaunchedEffect(cachedNames) {
        timelineViewModel.updateNames(own = cachedNames.own, partner = cachedNames.partner, coralSignerId = cachedNames.coralSignerId)
    }
    LaunchedEffect(readIds) { timelineViewModel.updateReadIds(readIds) }
    // A parked rejection is invisible to the sync engine from now on, so the queue
    // is the only place the timeline can learn that a row will not fix itself.
    val rejectedEntityIds by database.outboxDao().observeRejectedEntityIds().collectAsState(initial = emptyList())
    LaunchedEffect(rejectedEntityIds) {
        timelineViewModel.updateRejectedIds(rejectedEntityIds.toSet())
    }
    val albumMedia = remember(roomEntries, roomBlocks, cachedNames) {
        albumMediaOf(
            entries = roomEntries,
            blocks = roomBlocks,
            currentUserId = SyncSession.load(context)?.userId,
            names = cachedNames,
        )
    }
    val cityStories = remember(roomEntries, roomBlocks, cachedNames, cityScope) {
        cityStoriesOf(
            entries = roomEntries,
            blocks = roomBlocks,
            currentUserId = cityScope?.second,
            currentCoupleId = cityScope?.first,
            names = cachedNames,
        )
    }
    val cityDataOverview = remember(cityStories) {
        CityMemoryIndex(cityStories).let { "${it.cities.size} 座有故事的城市 · ${it.provinceCount} 个省级地区 · ${it.recordCount} 篇城市记录" }
    }
    val reviewMemories by androidx.compose.runtime.produceState<List<ReviewMemoryUi>>(
        initialValue = emptyList(), roomEntries, roomBlocks, cachedNames, recordingScope,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            buildReviewMemories(roomEntries, roomBlocks, recordingSession.userId, recordingSession.coupleId, cachedNames)
        }
    }

    // Unpublished text is kept off the critical path: restore on entry, keep
    // on every exit except a successful save.
    LaunchedEffect(editorState.saved) {
        if (editorState.saved) {
            if (perspectiveEntryId != null) editingMode?.let { DraftStore.clear(draftContext, it) }
        }
    }

    // A share or mode hand-off can arrive before the 300ms debounce / ON_STOP.
    // Persist the old owner's latest VM state before changing IDs or resetting it.
    val keepCurrentDraft: () -> Unit = {
        editingMode?.let {mode ->
            val state=editorViewModel.state.value
            if(!state.saved) {
                val occurrence=state.occurrenceTime.takeIf {state.occurrenceEdited}
                if(perspectiveEntryId!=null) DraftStore.save(draftContext,mode,state.title,state.body,state.photos,
                    state.attachments,occurrence,state.visibility)
                else activeDraftId?.let {id -> draftLibrary.save(id,mode,
                    DraftStore.Draft(state.title,state.body,state.photos,state.attachments,occurrence,state.visibility))}
                savedDrafts=draftLibrary.list()
            }
        }
    }
    /** Explicit fresh pages (including Sharesheet imports) never replace an unfinished slot. */
    val openFreshEditor: (EntryMode) -> Unit = { mode ->
        keepCurrentDraft()
        perspectiveEntryId = null
        publishedEntryId = null
        activeDraftId = openDraftEditor(draftLibrary,mode,editorViewModel,fresh=true)
        draftBoxOpen = false
        editingMode = mode
    }
    val openEditor: (EntryMode) -> Unit = { mode ->
        keepCurrentDraft()
        perspectiveEntryId = null
        publishedEntryId = null
        activeDraftId = openDraftEditor(draftLibrary,mode,editorViewModel)
        draftBoxOpen = false
        editingMode = mode
    }

    // Android Sharesheet hand-offs land in the same editor as the +记录 tab.
    // The request counter lets a second share reopen the editor while the app
    // is already visible; the mode remains explicit for future shared drafts.
    LaunchedEffect(initialEditorRequest) {
        if (initialEditorRequest > 0) {
            littleNoteOpen = false
            val mode = runCatching { EntryMode.valueOf(initialEditorMode.orEmpty()) }
                .getOrDefault(EntryMode.PERSONAL)
            openFreshEditor(mode)
            val shareContext = incomingShareDraftContext(context)
            DraftStore.load(shareContext, mode)?.let { incoming ->
                activeDraftId?.let { draftLibrary.save(it, mode, incoming) }
                editorViewModel.restore(incoming.title, incoming.body, incoming.photos, incoming.occurrence, incoming.attachments, incoming.visibility)
                DraftStore.clear(shareContext, mode)
            }
        }
    }

    if (littleNoteOpen) {
        LittleNoteRoute(onClose = { littleNoteOpen = false })
        return
    }

    editingMode?.let { mode ->
        val ownedDraftId=activeDraftId
        val ownedPerspective=perspectiveEntryId
        val ownedContext=draftContext
        val persistDraft: (com.twomemory.editor.EditorUiState) -> Unit = { state ->
            if (ownedPerspective != null) DraftStore.save(ownedContext, mode, state.title, state.body, state.photos,
                state.attachments, state.occurrenceTime.takeIf { state.occurrenceEdited }, state.visibility)
            else ownedDraftId?.let { id ->
                draftLibrary.save(id, mode, DraftStore.Draft(state.title, state.body, state.photos, state.attachments,
                    state.occurrenceTime.takeIf { state.occurrenceEdited }, state.visibility))
                savedDrafts = draftLibrary.list()
            }
        }
        val latestOwnedState = {
            if (editingMode == mode && activeDraftId == ownedDraftId && perspectiveEntryId == ownedPerspective)
                editorViewModel.state.value else null
        }
        val keepDraft = { latestOwnedState()?.takeUnless { it.saved }?.let(persistDraft); Unit }
        val manuallySaveDraft = {
            finishOwnedDraft(latestOwnedState, persistDraft) {
                ownedDraftId?.let(draftLibrary::markManualSaved)
                editingMode = null
            }.onFailure {
                if (latestOwnedState() != null) editorViewModel.reportPhotoFailure("草稿状态尚未保存，请重试；内容仍保留")
            }
            Unit
        }
        val closeEditor = {
            if (latestOwnedState() != null) {
                if (!editorViewModel.state.value.saved) keepDraft()
                editingMode = null
            }
        }
        EditorDraftGuard(editorState,"$recordingScope|$mode|$ownedDraftId|$ownedPerspective", latest={
            if (editingMode==mode && activeDraftId==ownedDraftId && perspectiveEntryId==ownedPerspective)
                editorViewModel.state.value else null
        }, persist=persistDraft)
        val switchMode: (EntryMode) -> Unit = { next ->
            if (next != mode && latestOwnedState() != null) {
                keepDraft()
                openEditor(next)
            }
        }
        BackHandler { closeEditor() }
        val photoActions = rememberEditorPhotoActions(editorViewModel)
        val attachmentActions = rememberEditorAttachmentActions(editorViewModel)
        val delivery = rememberDeliveryStatus(publishedEntryId)
        val retryDelivery: () -> Unit = {
            publishedEntryId?.let { id -> toolScope.launch { SyncSession.retrySync(context, id) } }
        }
        if (mode == EntryMode.PERSONAL) {
            PersonalEditorScreen(
                state = editorState,
                onTitleChange = editorViewModel::updateTitle,
                onBodyChange = editorViewModel::updateBody,
                onVisibilityChange = editorViewModel::updateVisibility,
                onPublish = {
                    val submittedId = activeDraftId
                    editorViewModel.publish { state ->
                        val published = SyncSession.publish(context, state, mode)
                        if (activeDraftId == submittedId) publishedEntryId = published
                        submittedId?.let { draftLibrary.removeIfUnchanged(it, DraftStore.Draft(state.title, state.body, state.photos,
                            state.attachments, state.occurrenceTime.takeIf { state.occurrenceEdited }, state.visibility)) }
                        savedDrafts = draftLibrary.list()
                    }
                },
                onClose = closeEditor,
                mode = mode,
                onModeChange = switchMode,
                photoActions = photoActions,
                attachmentActions = attachmentActions,
                ownName = cachedNames.own,
                delivery = delivery,
                onRetryDelivery = retryDelivery,
                onOccurrenceChange = editorViewModel::updateOccurrence,
                onSaveDraft = manuallySaveDraft,
            )
        } else {
            val references = reviewMemories.firstOrNull { it.id == perspectiveEntryId?.toString() }?.blocks.orEmpty()
                .mapIndexed { index, block -> com.twomemory.editor.EditorReferenceBlock(
                    id=block.id, authorId=block.authorId, type=block.type, orderKey=index.toLong(),
                    text=block.text, localPath=block.localPath, assetId=block.assetId, payload=block.payload,
                ) }
            SharedEditorRoute(
                state = editorState,
                partnerName = cachedNames.partner,
                ownAvatar = ownAvatarBitmap,
                partnerAvatar = partnerAvatarBitmap,
                currentUserId = recordingSession.userId,
                contributorIds = references.mapNotNull { it.authorId }.toSet(),
                referenceBlocks = references,
                onPublish = {
                    val submittedId = activeDraftId
                    val submittedPerspective = perspectiveEntryId
                    editorViewModel.publish { state ->
                        val published = submittedPerspective?.let { SyncSession.appendPerspective(context, it, state) }
                            ?: SyncSession.publish(context, state, mode)
                        if (activeDraftId == submittedId && perspectiveEntryId == submittedPerspective) publishedEntryId = published
                        submittedId?.let { draftLibrary.removeIfUnchanged(it, DraftStore.Draft(state.title, state.body, state.photos,
                            state.attachments, state.occurrenceTime.takeIf { state.occurrenceEdited }, state.visibility)) }
                        savedDrafts = draftLibrary.list()
                    }
                },
                onTitleChange = editorViewModel::updateTitle,
                onBodyChange = editorViewModel::updateBody,
                onClose = closeEditor,
                onModeChange = switchMode,
                photoActions = photoActions,
                attachmentActions = attachmentActions,
                ownName = cachedNames.own,
                delivery = delivery,
                onRetryDelivery = retryDelivery,
                onOccurrenceChange = editorViewModel::updateOccurrence,
                onSaveDraft = manuallySaveDraft,
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
                        entries.firstOrNull()?.takeUnless { it.deleted }?.let { entity ->
                            com.twomemory.model.EntryDetail(
                                entry = entity.toTimelineItem(previewOf(blocks), photoOf(blocks), attachmentsOf(blocks), notePaperOf(blocks)),
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
                },
                currentUserId = SyncSession.load(context)?.userId,
                syncComment = { SyncSession.triggerSync(context) },
            )
        }
        LaunchedEffect(cachedNames) {
            detailViewModel.updateNames(cachedNames.own, cachedNames.partner, cachedNames.coralSignerId)
        }
        val openUuid = remember(entryId) { runCatching { java.util.UUID.fromString(entryId) }.getOrNull() }
        val detailRows by remember(entryId) { database.outboxDao().observeForEntity(entryId) }
            .collectAsState(initial = emptyList())
        val entity = roomEntries.firstOrNull { it.id == entryId }
        LaunchedEffect(entity?.id, entity?.state) {
            if (entity?.state == "PUBLISHED" && entity.authorId != recordingSession.userId.toString()) {
                readingProgress.markRead(entryId)
                readIds = readingProgress.readIds()
            }
        }
        val delivery = entity?.let { detailDeliveryStatus(it.state, detailRows) }
        BackHandler { openEntryId = null }
        EntryDetailRoute(
            detailViewModel,
            partnerAvatar = partnerAvatarBitmap,
            onBack = { openEntryId = null },
            delivery = delivery,
            onRetryDelivery = {
                openUuid?.let { id -> toolScope.launch { SyncSession.retrySync(context, id) } }
            },
            ownAvatar = ownAvatarBitmap,
            privacyAction = {
                if (entity != null && !entity.deleted) Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    BookmarkToggle(entryId in bookmarkIds) {
                        runCatching { bookmarkStore.toggle(entryId) }
                            .onSuccess { bookmarkIds = it; bookmarkError = null }
                            .onFailure { bookmarkError = "书签暂未保存，请重试" }
                    }
                    bookmarkError?.let { Text(it, style = com.twomemory.designsystem.TwoMemoryTypography.caption) }
                }
                if (entity?.mode == "PERSONAL" && entity.state == "PUBLISHED" && entity.authorId == recordingSession.userId.toString() && !entity.deleted)
                    RecordPrivacyAction(entity, database)
            },
            onAddPerspective = if (entity?.mode == "COLLABORATIVE" &&
                (entity.state == "PUBLISHED" || detailRows.any { it.action == "PUBLISH_ENTRY" }) && openUuid != null) ({
                toolScope.launch {
                    val binding = SyncSession.captureBinding(context) ?: return@launch
                    val ownId = binding.session.userId.toString()
                    val original = database.entryDao().blocks(entryId)
                    if (!SyncSession.isCurrentBinding(context, binding)) return@launch
                    val otherText = original.filter { runCatching { java.util.UUID.fromString(it.authorId) }.isSuccess &&
                            it.authorId != ownId && it.type == "TEXT" && !it.deleted }
                        .joinToString("\n\n") { runCatching { JSONObject(it.payload).optString("text") }.getOrDefault("") }
                    val ownText = original.filter { it.authorId == ownId && it.type == "TEXT" && !it.deleted }
                        .joinToString("\n\n") { runCatching { JSONObject(it.payload).optString("text") }.getOrDefault("") }
                    editorViewModel.reset()
                    perspectiveEntryId = openUuid
                    activeDraftId = null
                    publishedEntryId = null
                    DraftStore.load(perspectiveDraftContext(context, openUuid), EntryMode.COLLABORATIVE)?.let {
                        editorViewModel.restore(it.title, it.body, it.photos, it.occurrence, it.attachments, it.visibility)
                    }
                    editorViewModel.setSharedContext(cachedNames.partner, otherText, entity.title.orEmpty(), ownPublishedBody = ownText,
                        occurrenceTime = java.time.Instant.ofEpochMilli(entity.occurredAtEpochMillis), timezone = entity.occurredTimezone)
                    editingMode = EntryMode.COLLABORATIVE
                    openEntryId = null
                }
            }) else null,
        )
        return
    }

    if (occasionsOpen) {
        BackHandler { occasionsOpen = false }
        key(recordingScope) { OccasionLibrary(occasionCards, recordingSession.userId.toString(), recordingSession.coupleId.toString(), onBack = { occasionsOpen = false }) }
        return
    }
    toolRoute?.let { route ->
        BackHandler { toolRoute = null }
        if (route == CoupleToolRoute.ANNIVERSARY) {
            IndependentAnniversaryScreen(independentAnniversaryState,
                onDraftChange = independentAnniversary::editDraft,
                onSave = { toolScope.launch { independentAnniversary.save() } },
                onBack = { if (!independentAnniversaryState.saving) toolRoute = null })
            return
        }
        if (route == CoupleToolRoute.CAPSULE) {
            key(recordingScope) { CapsuleLibrary(recordingSession.userId.toString(), recordingSession.coupleId.toString(), onBack = { toolRoute = null }) }
            return
        }
        RelationshipToolsScreen(
            route = route,
            onBack = { toolRoute = null },
            onSaveAnniversary = { draft ->
                anniversaryName = draft.name
                anniversaryDate = draft.date
                anniversaryRepeats = draft.repeatsYearly
                anniversaryShared = false
                toolsPrefs.edit()
                    .putString("anniversaryName", draft.name)
                    .putString("anniversaryDate", draft.date)
                    .putBoolean("anniversaryRepeats", draft.repeatsYearly)
                    .putString("anniversaryReminderTime", draft.reminderTime)
                    .apply()
                com.twomemory.app.notifications.LocalRelationshipReminderScheduler.schedule(context)
                toolScope.launch {
                    runCatching {
                        SyncSession.saveAnniversary(
                            context,
                            draft.name,
                            draft.date,
                            draft.repeatsYearly,
                        )
                    }.onSuccess { anniversaryShared = true }
                }
            },
            onSaveCapsule = { draft ->
                // No capsule endpoint exists on the server yet, so the letter stays on
                // this device — same plaintext preferences DraftStore already uses for
                // unsent text. Hidden here until the unlock date; never in an export.
                toolsPrefs.edit()
                    .putString("capsuleTitle", draft.title)
                    .putString("capsuleBody", draft.body)
                    .putString("capsuleUnlockDate", draft.unlockDate)
                    .putBoolean("capsuleLocked", true)
                    .apply()
                com.twomemory.app.notifications.LocalRelationshipReminderScheduler.schedule(context)
            },
            onStartExport = { scope, from, to ->
                toolScope.launch {
                    if (scope == com.twomemory.couple.ExportScope.MEMORY_BOOK || scope == com.twomemory.couple.ExportScope.RECORD_ARCHIVE) {
                        if (memoryBookBusy) return@launch
                        memoryBookBusy = true
                        exportStatus = "正在整理回忆与原媒体…"
                        var producedBook: java.io.File? = null
                        try {
                            val session = SyncSession.load(context)
                            val names = SyncSession.loadNames(context)
                            val book = if (scope == com.twomemory.couple.ExportScope.RECORD_ARCHIVE)
                                RecordArchiveExporter.build(context, database, session?.userId, names.own, names.partner)
                            else MemoryBookExporter.build(context, database, session?.userId, names.own, names.partner)
                            producedBook = book.file
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val current = SyncSession.load(context)
                            check(current?.storageOrigin == session?.storageOrigin && current?.coupleId == session?.coupleId && current?.userId == session?.userId) {
                                book.file.delete()
                                "空间已切换，请重新导出"
                            }
                            pendingMemoryBookName = book.file.name
                            exportStatus = "纪念册已整理，请选择保存位置"
                            bookSavePicker.launch("MoonLetter_${if (scope == com.twomemory.couple.ExportScope.RECORD_ARCHIVE) "记录存档" else "回忆手书"}_${LocalDate.now()}.zip")
                        } catch (failure: Exception) {
                            producedBook?.delete()
                            pendingBookFile(context, pendingMemoryBookName)?.delete(); pendingMemoryBookName = null
                            memoryBookBusy = false
                            exportStatus = "纪念册没完成：${failure.message.orEmpty()}"
                        }
                        return@launch
                    }
                    val bounds = if (scope == com.twomemory.couple.ExportScope.DATE_RANGE) {
                        val start = from?.let { exportInstant(it, endOfDay = false) }
                        val end = to?.let { exportInstant(it, endOfDay = true) }
                        if ((from != null && start == null) || (to != null && end == null)) {
                            exportStatus = "日期没认出来，请写成 2026-10-01 这样的形式"
                            return@launch
                        }
                        start to end
                    } else null to null
                    exportStatus = "正在准备导出…"
                    val session = SyncSession.load(context)
                    val outcome = runCatching {
                        if (scope == com.twomemory.couple.ExportScope.LOCAL_CACHE) {
                            val names = SyncSession.loadNames(context)
                            val document = LocalExportBuilder.build(
                                database = database,
                                currentUserId = session?.userId,
                                ownName = names.own,
                                partnerName = names.partner,
                            )
                            "月笺本机缓存导出（Markdown + JSON）" to
                                (document.markdown + "\n\n---\n\n# 机器可读 JSON\n\n" + document.json)
                        } else {
                            val bound = session
                                ?: throw IllegalStateException("这台手机还没有绑定空间，服务端没有可读的记录")
                            val json = com.twomemory.network.RetrofitSessionApi.create { bound.token }
                                .exportSpace(bound.baseUrl, bound.token, bound.coupleId, bounds.first, bounds.second)
                            "月笺服务端导出（JSON）" to json
                        }
                    }
                    outcome.fold(
                        onSuccess = { (subject, text) ->
                            when {
                                text.isBlank() -> exportStatus = "这次导出是空的：还没有可读到的记录"
                                text.length > MAX_SHARE_CHARS ->
                                    exportStatus = "导出的文本太长，分享面板放不下；请用「按时间范围」分批导出"
                                else -> {
                                    exportStatus = null
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, subject)
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(share, "分享月笺导出"))
                                }
                            }
                        },
                        onFailure = { failure ->
                            exportStatus = "导出没完成：${failure.message ?: "请重试"}"
                        },
                    )
                }
            },
            exportStatus = exportStatus,
            anniversaryShared = anniversaryShared,
            initialAnniversaryName = anniversaryName,
            initialAnniversaryDate = anniversaryDate,
            initialAnniversaryRepeats = anniversaryRepeats,
            initialCapsuleTitle = toolsPrefs.getString("capsuleTitle", "").orEmpty(),
            initialCapsuleBody = toolsPrefs.getString("capsuleBody", "").orEmpty(),
            initialCapsuleUnlockDate = toolsPrefs.getString("capsuleUnlockDate", "").orEmpty(),
            initialCapsuleLocked = toolsPrefs.getBoolean("capsuleLocked", false),
            initialAnniversaryReminderTime = toolsPrefs.getString("anniversaryReminderTime", "09:00").orEmpty(),
            onSendTestNotification = { com.twomemory.app.notifications.LocalReminderNotifier.sendTest(context) },
            onOpenNotificationSettings = { com.twomemory.app.notifications.LocalReminderNotifier.openSettings(context) },
        )
        return
    }

    reviewRoute?.let { route ->
        BackHandler { reviewRoute = null }
        reviewStateHolder.SaveableStateProvider(route.name) {
            MemoryReviewScreen(
                route = route,
                memories = reviewMemories,
                onBack = { reviewRoute = null },
                onOpenEntry = { openEntryId = it },
                today = occasionToday,
                onOpenBook = { reviewRoute = MemoryReviewRoute.WEEKLY_SUMMARY },
            )
        }
        return
    }

    if (draftBoxOpen) {
        BackHandler { draftBoxOpen = false }
        DraftLibraryScreen(savedDrafts, onBack = { draftBoxOpen = false }, onNew = openFreshEditor, onResume = { saved ->
            draftLibrary.activate(saved.id, saved.mode)
            editorViewModel.reset()
            perspectiveEntryId = null; publishedEntryId = null; activeDraftId = saved.id
            editorViewModel.restore(saved.draft.title, saved.draft.body, saved.draft.photos, saved.draft.occurrence, saved.draft.attachments, saved.draft.visibility)
            editingMode = saved.mode; draftBoxOpen = false
        }, onDelete = { saved -> draftLibrary.remove(saved.id); savedDrafts = draftLibrary.list() })
        return
    }

    val dueOccasions = remember(recordingScope, occasionStore, occasionCards, occasionToday, occasionDismissRevision) {
        occasionStore.due(recordingSession.userId.toString(), occasionToday)
    }
    if (dueOccasions.isNotEmpty()) OccasionGreeting(dueOccasions, {
        dueOccasions.forEach { occasionStore.dismiss(it, occasionToday) }
        occasionDismissRevision++
    })
    Scaffold(
        contentWindowInsets = if (selectedKey == "timeline") WindowInsets(0, 0, 0, 0)
            else WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = {
            MoonLetterBottomNavigation(selectedKey) { tab ->
                if (tab.key == "create") {
                    openEditor(EntryMode.PERSONAL)
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
                    listState = timelineListState,
                    coverBitmap = coverBitmap,
                    onChangeCover = { coverPicker.launch(arrayOf("image/*")) },
                    onOpen = { openEntryId = it },
                    onCoverVisible = { timelineCoverVisible = it },
                    onCreate = { littleNoteOpen = true },
                    ownAvatar = ownAvatarBitmap,
                    partnerAvatar = partnerAvatarBitmap,
                    onOpenDrafts = { savedDrafts = draftLibrary.list(); draftBoxOpen = true },
                    onOpenBookmarks = { bookmarksOpen = true },
                )
                "couple" -> CoupleRoute(
                    viewModel = coupleViewModel,
                    cityDataOverview = cityDataOverview,
                    versionLabel = "M1 · ${BuildConfig.VERSION_NAME}",
                    serverAddress = SyncSession.load(context)?.baseUrl.orEmpty(),
                    onOpenConnection = {
                        context.startActivity(android.content.Intent(context, CloudConnectionActivity::class.java))
                    },
                    onThemeChange = onThemeChange,
                    serverOwnName = cachedNames.own,
                    serverPartnerName = cachedNames.partner,
                    onSaveName = { name ->
                        val saved = SyncSession.renameOwn(context, name)
                        cachedNames = SyncSession.loadNames(context)
                        timelineViewModel.updateNames(
                            own = cachedNames.own.ifBlank { saved },
                            partner = cachedNames.partner,
                            coralSignerId = cachedNames.coralSignerId,
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
                    onOpenOccasions = { occasionsOpen = true },
                    onOpenCapsule = { toolRoute = CoupleToolRoute.CAPSULE },
                    onOpenExport = { toolRoute = CoupleToolRoute.EXPORT },
                    onOpenPastToday = { reviewRoute = MemoryReviewRoute.PAST_TODAY },
                    onOpenWeeklySummary = { reviewRoute = MemoryReviewRoute.WEEKLY_SUMMARY },
                    anniversaryName = independentAnniversaryState.confirmed?.title ?: "我们的纪念日",
                    anniversaryDate = independentAnniversaryState.confirmed?.let { date ->
                        val year = date.startYear
                        if (date.calendarType == "GREGORIAN" && year != null)
                            runCatching { LocalDate.of(year, date.month, date.day).toString() }.getOrDefault("日期待确认")
                        else "${date.month}月${date.day}日 · 原始年份或日历规则待确认"
                    } ?: "选好起始日期，把这一天留给我们",
                    anniversaryRepeats = independentAnniversaryState.confirmed?.repeatsYearly ?: true,
                    onSendTestNotification = { com.twomemory.app.notifications.LocalReminderNotifier.sendTest(context) },
                    onOpenNotificationSettings = { com.twomemory.app.notifications.LocalReminderNotifier.openSettings(context) },
                )
                "album" -> IndependentAlbumScreen(database)
                "map" -> {
                    val mapSession = SyncSession.load(context)
                    val mapScope = mapSession?.let { "${it.storageOrigin}|${it.coupleId}" } ?: "local"
                    val mapPrefs = remember(context) { context.getSharedPreferences("moon_letter_map_view", android.content.Context.MODE_PRIVATE) }
                    val lastCity = remember(mapScope) { mapPrefs.getString(mapScope, null) }
                    CityMapPreviewScreen(
                    stories = cityStories,
                    lastBrowsedCity = lastCity,
                    memoryScope = mapScope,
                    onRememberCity = { mapPrefs.edit().putString(mapScope, it).apply() },
                    onOpenEntry = { openEntryId = it },
                    onCreateInCity = { city ->
                        openEditor(EntryMode.PERSONAL)
                        editorViewModel.addCity(city)
                    },
                )
                }
            }
        }
    }
    if (bookmarksOpen) MemoryBookmarksDialog(bookmarkEntries, bookmarkIds,
        onOpenEntry = { bookmarksOpen = false; openEntryId = it },
        onRemoveBookmark = { id -> runCatching { bookmarkStore.remove(id) }
            .onSuccess { bookmarkIds = it; bookmarkError = null }
            .onFailure { bookmarkError = "书签暂未保存，请重试" } },
        onDismiss = { bookmarksOpen = false }, errorMessage = bookmarkError)
}
