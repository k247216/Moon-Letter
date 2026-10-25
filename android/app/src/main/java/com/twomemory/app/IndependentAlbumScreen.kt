package com.twomemory.app
import com.twomemory.designsystem.PaperTextButton as TextButton

import android.graphics.BitmapFactory
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.paperTexture
import com.twomemory.database.AlbumCategoryEntity
import com.twomemory.database.AlbumPhotoEntity
import com.twomemory.database.AlbumRepository
import com.twomemory.database.AlbumWithPhotos
import com.twomemory.database.AppDatabase
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AlbumPaper = TwoMemoryColors.WarmBeigeBackground
private val AlbumInk = TwoMemoryColors.WarmBeigeInk
private val AlbumMuted = TwoMemoryColors.WarmBeigeMuted
private val AlbumCoral = TwoMemoryColors.WarmBeigeAccent
private val AlbumLine = TwoMemoryColors.WarmBeigeLine
private val AlbumSerif = TwoMemoryTypography.display
private val AlbumBody = TwoMemoryTypography.body

private data class AlbumLibrary(val albums: List<AlbumWithPhotos>, val categories: List<AlbumCategoryEntity>)

/** Standalone entry point. Owns saved album/category/viewer state and never reads diary entries. */
@Composable
fun IndependentAlbumScreen(database: AppDatabase) {
    val context = LocalContext.current
    val coverColumns = when {
        LocalConfiguration.current.screenWidthDp >= 840 -> 4
        LocalConfiguration.current.screenWidthDp >= 600 -> 3
        else -> 2
    }
    val session = remember { SyncSession.load(context) }
    val repository = remember(database, context, session) { AlbumRepository(database, context,
        session?.let { com.twomemory.database.AlbumScope(it.storageOrigin, it.coupleId.toString(), it.userId.toString()) }) }
    val scope = rememberCoroutineScope()
    var library by remember(database) { mutableStateOf<AlbumLibrary?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var openAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val viewPrefs = remember(context.applicationContext) {
        context.applicationContext.getSharedPreferences("moon_letter_album_view", android.content.Context.MODE_PRIVATE)
    }
    var showPhotos by rememberSaveable { mutableStateOf(viewPrefs.getBoolean("showPhotos", false)) }
    fun selectAlbumView(photos: Boolean) {
        // apply updates preferences in memory before navigation can dispose this screen.
        viewPrefs.edit().putBoolean("showPhotos", photos).apply()
        showPhotos = photos
    }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var viewerPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var creationError by remember { mutableStateOf<String?>(null) }
    var deleteAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    fun syncAlbums() { AlbumCloudSync.enqueue(context); AlbumCloudSync.schedule(context) }

    LaunchedEffect(repository, retry) {
        loadError = false
        try {
            combine(repository.observeAlbums(), repository.observeCategories(), ::AlbumLibrary).collect { library = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadError = true
        }
    }
    val albums = library?.albums.orEmpty()
    val categories = library?.categories.orEmpty().filter { category -> albums.any { it.album.categoryId == category.id } }
    val openAlbum = albums.firstOrNull { it.album.id == openAlbumId }
    val selectedAlbums = albums.filter { categoryId == null || it.album.categoryId == categoryId }
    val photos = (if (openAlbum != null) openAlbum.orderedPhotos else selectedAlbums.flatMap { it.photos })
        .sortedWith(compareByDescending<AlbumPhotoEntity> { it.takenAtEpochMillis }.thenBy { it.id })

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        val target = pickerAlbumId
        pickerAlbumId = null
        if (uris.isNotEmpty() && target != null) {
            importing = true
            scope.launch {
                try {
                    val result = repository.importPhotos(target, uris)
                    syncAlbums()
                    notice = if (result.failures.isEmpty()) "已添加 ${result.importedCount} 张照片"
                    else "已保存 ${result.importedCount} 张，${result.failures.size} 张未保存。${result.failures.first().reason}"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    notice = "保存失败，请重新打开相册后重试"
                } finally { importing = false }
            }
        }
    }
    fun addPhotos() {
        val target = openAlbum?.album?.id ?: return
        if (importing || pickerAlbumId != null) return
        pickerAlbumId = target
        try {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: Exception) {
            pickerAlbumId = null
            notice = "无法打开照片选择器，请稍后重试"
        }
    }

    BackHandler(enabled = openAlbumId != null && viewerPhotoId == null) { openAlbumId = null }
    Column(Modifier.fillMaxSize().paperTexture(AlbumPaper).padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (openAlbumId != null) TextButton(onClick = { openAlbumId = null }, contentPadding = PaddingValues(end = 10.dp)) {
                Text("‹ 返回", color = AlbumCoral)
            }
            Text(openAlbum?.album?.name ?: "共同相册", Modifier.weight(1f), style = AlbumSerif, color = AlbumInk,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { if (openAlbum != null) addPhotos() else showCreate = true },
                enabled = library != null && !loadError && !importing && pickerAlbumId == null) {
                Icon(if (openAlbum != null) TwoMemoryIcons.Image else TwoMemoryIcons.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (openAlbum != null) "添加照片" else "新建相册", color = AlbumCoral)
            }
        }
        AlbumUnderline(Modifier.width(168.dp).height(12.dp))
        Spacer(Modifier.height(8.dp))
        Text("把我们的日子，好好收起来", style = MaterialTheme.typography.labelSmall, color = AlbumMuted)
        if (openAlbum != null) {
            val category = categories.firstOrNull { it.id == openAlbum.album.categoryId }?.name.orEmpty()
            Text("$category  /  ${photos.size} 张照片", Modifier.padding(top = 6.dp), style = AlbumBody, color = AlbumMuted)
            if (openAlbum.album.createdBy == null || openAlbum.album.createdBy == session?.userId?.toString())
                TextButton(onClick = { deleteAlbumId = openAlbum.album.id }, modifier = Modifier.testTag("album-delete")) {
                    Text("删除相册", style = MaterialTheme.typography.labelSmall, color = AlbumMuted)
                }
        } else {
            Row(Modifier.padding(top = 18.dp).border(1.dp, AlbumLine, RoundedCornerShape(28.dp)).padding(3.dp)) {
                AlbumTab("照片", showPhotos) { selectAlbumView(true) }
                AlbumTab("相册集", !showPhotos) { selectAlbumView(false) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text("全部") })
                categories.forEach { category ->
                    FilterChip(selected = categoryId == category.id, onClick = { categoryId = category.id },
                        label = { Text(category.name) })
                }
            }
        }
        if (importing) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = AlbumCoral)
            Text("正在复制照片到相册…", Modifier.padding(vertical = 8.dp), style = AlbumBody, color = AlbumMuted)
        }
        notice?.let { message ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color(0xFFF1E5D9), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = AlbumInk, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { notice = null }) { Text("知道了", color = AlbumCoral) }
            }
        }
        when {
            loadError -> AlbumEmpty("相册暂时无法读取", "照片不会因此被删除，请重试。", "重试") { retry++ }
            library == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AlbumCoral)
            }
            openAlbumId != null && openAlbum == null -> AlbumEmpty("找不到这个相册", "返回相册集，选择一个相册继续。", "返回相册集") { openAlbumId = null }
            openAlbum != null || showPhotos -> {
                if (photos.isEmpty()) AlbumEmpty("还没有照片",
                    if (openAlbum != null) "把值得留住的片刻，放进这个相册。" else "先创建一个相册，再添加你自己的照片。",
                    if (openAlbum != null) "添加照片" else "查看相册集", enabled = !importing) {
                    if (openAlbum != null) addPhotos() else selectAlbumView(false)
                } else AlbumMosaic(photos, Modifier.weight(1f)) { viewerPhotoId = it.id }
            }
            selectedAlbums.isEmpty() -> AlbumEmpty("留一页给回忆", "为旅行、日常或特别的日子，建立你的第一个相册。", "新建相册") { showCreate = true }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                item {
                    Text("把日子，收进一本相册", style = AlbumBody, color = AlbumMuted)
                    Text("${selectedAlbums.size} 本相册 · ${selectedAlbums.sumOf { it.photos.size }} 张照片",
                        Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = AlbumMuted)
                }
                selectedAlbums.chunked(coverColumns).forEach { pair ->
                    item(key = pair.first().album.id) {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            pair.forEach { album ->
                                AlbumCover(album, categories.firstOrNull { it.id == album.album.categoryId }?.name.orEmpty(),
                                    Modifier.weight(1f)) { openAlbumId = album.album.id }
                            }
                            repeat(coverColumns - pair.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) AlbumCreateDialog(categories, creating, creationError,
        onDismiss = { if (!creating) { showCreate = false; creationError = null } },
        onCreate = { name, category ->
            creating = true
            creationError = null
            scope.launch {
                try {
                    openAlbumId = repository.createAlbum(name, category)
                    syncAlbums()
                    showCreate = false
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: IllegalArgumentException) { creationError = error.message
                } catch (_: Exception) { creationError = "相册创建失败，请重试" }
                finally { creating = false }
            }
        })
    val viewerIndex = photos.indexOfFirst { it.id == viewerPhotoId }
    if (viewerIndex >= 0) AlbumPhotoViewer(photos, viewerIndex,
        onClose = { viewerPhotoId = null }, onNavigate = { viewerPhotoId = it.id },
        canDelete = { it.addedBy == null || it.addedBy == session?.userId?.toString() },
        onDelete = { deletePhotoId = it.id },
        onCover = { photo ->
            scope.launch {
                try {
                    notice = if (repository.setCover(photo.albumId, photo.id)) "已设为相册封面" else "无法设置封面，请重新打开相册"
                    syncAlbums()
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { notice = "封面保存失败，请重试" }
                viewerPhotoId = null
            }
        })
    if (deleteAlbumId != null || deletePhotoId != null) AlertDialog(
        onDismissRequest = { deleteAlbumId = null; deletePhotoId = null },
        title = { Text(if (deleteAlbumId != null) "删除这本相册？" else "移除这张照片？") },
        text = { Text("这项修改会同步到对方。已经保存到手机相册的副本不会被删除。") },
        confirmButton = { TextButton(onClick = {
            val album = deleteAlbumId; val photo = deletePhotoId
            scope.launch {
                try {
                    if (album != null) { repository.deleteAlbum(album); openAlbumId = null }
                    else if (photo != null) repository.deletePhoto(photo)
                    viewerPhotoId = null
                    syncAlbums()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { notice = error.message ?: "操作失败，请重试" }
                finally { deleteAlbumId = null; deletePhotoId = null }
            }
        }) { Text("确认删除", color = AlbumCoral) } },
        dismissButton = { TextButton(onClick = { deleteAlbumId = null; deletePhotoId = null }) { Text("取消") } },
    )
}

@Composable
private fun AlbumTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(24.dp)).background(if (selected) Color(0xFFF1DDD4) else Color.Transparent)
        .testTag(if (label == "照片") "album-view-photos" else "album-view-sets")
        .clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 32.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, style = AlbumBody, color = if (selected) AlbumCoral else AlbumMuted)
    }
}

@Composable
private fun AlbumUnderline(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val line = Path().apply {
            moveTo(0f, size.height * .7f)
            cubicTo(size.width * .2f, 0f, size.width * .42f, 0f, size.width * .4f, size.height * .5f)
            cubicTo(size.width * .36f, size.height, size.width * .68f, size.height * .15f, size.width, size.height * .35f)
        }
        drawPath(line, AlbumCoral, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun AlbumEmpty(title: String, body: String, action: String, enabled: Boolean = true, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 42.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(90.dp).rotate(-5f).border(1.dp, AlbumLine, RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .6f)),
            contentAlignment = Alignment.Center) { Icon(TwoMemoryIcons.Album, null, Modifier.size(36.dp), tint = AlbumMuted) }
        Text(title, Modifier.padding(top = 24.dp), style = AlbumSerif.copy(fontSize = 23.sp), color = AlbumInk)
        Text(body, Modifier.padding(top = 10.dp, bottom = 20.dp), style = AlbumBody,
            color = AlbumMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onAction, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = AlbumCoral)) { Text(action) }
    }
}

@Composable
private fun AlbumCover(album: AlbumWithPhotos, category: String, modifier: Modifier, onClick: () -> Unit) {
    val cover = album.cover
    val coverRatio = cover?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height }?.coerceIn(.85f, 1.8f) ?: 1f
    Column(modifier.clickable(onClick = onClick).testTag("album-cover-${album.album.id}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(coverRatio).rotate(-1.5f).background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, AlbumLine, RoundedCornerShape(4.dp)).padding(7.dp)) {
            if (cover != null) AlbumLocalPhoto(cover, Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)), fit = false)
            else Column(Modifier.fillMaxSize().background(Color(0xFFF0E7DA)).padding(12.dp), verticalArrangement = Arrangement.Center) {
                Text(category, style = MaterialTheme.typography.labelSmall, color = AlbumCoral)
                Text(album.album.name, Modifier.padding(vertical = 10.dp), style = AlbumBody, color = AlbumInk, maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
                AlbumUnderline(Modifier.fillMaxWidth().height(10.dp))
                Text("等待第一张照片", Modifier.padding(top = 18.dp), style = MaterialTheme.typography.labelSmall, color = AlbumMuted)
            }
        }
        Text(album.album.name, Modifier.padding(top = 12.dp), style = AlbumBody, color = AlbumInk,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("$category · ${album.photos.size} 张", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall,
            color = AlbumMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumMosaic(photos: List<AlbumPhotoEntity>, modifier: Modifier, onPhoto: (AlbumPhotoEntity) -> Unit) {
    val columns = when {
        LocalConfiguration.current.screenWidthDp >= 840 -> 6
        LocalConfiguration.current.screenWidthDp >= 600 -> 4
        else -> 3
    }
    val months = remember(photos) { photos.groupBy { YearMonth.from(Instant.ofEpochMilli(it.takenAtEpochMillis).atZone(ZoneId.systemDefault())) } }
    LazyColumn(modifier, contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        months.forEach { (month, monthPhotos) ->
            item(key = "month-$month") {
                Row(Modifier.padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(AlbumCoral, RoundedCornerShape(50)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${month.year} 年 ${month.monthValue} 月", style = AlbumSerif.copy(fontSize = 22.sp), color = AlbumInk)
                        AlbumUnderline(Modifier.width(150.dp).height(8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${monthPhotos.size} 张", style = MaterialTheme.typography.labelSmall, color = AlbumMuted)
                }
            }
            item(key = "photos-$month") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(columns) { column ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            monthPhotos.filterIndexed { index, _ -> index % columns == column }.forEach { photo ->
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val ratio = if (photo.width > 0 && photo.height > 0)
                                        photo.width.toFloat() / photo.height else 1f
                                    AlbumLocalPhoto(photo, Modifier.fillMaxWidth()
                                        .height((maxWidth / ratio).coerceAtMost(220.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .testTag("album-photo-${photo.id}").clickable { onPhoto(photo) }, fit = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCreateDialog(categories: List<AlbumCategoryEntity>, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AlbumPaper,
        title = { Text("新建相册", style = AlbumSerif.copy(fontSize = 24.sp), color = AlbumInk) },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("给这些片刻一个名字", style = AlbumBody, color = AlbumMuted)
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("相册名称") }, singleLine = true, enabled = !busy, modifier = Modifier.testTag("album-name"))
                OutlinedTextField(value = category, onValueChange = { if (it.length <= 40) category = it },
                    label = { Text("相册分类 · 可选") }, singleLine = true, enabled = !busy, modifier = Modifier.testTag("album-category"))
                Text("分类整理整本相册，不标记单张照片。", style = MaterialTheme.typography.labelSmall, color = AlbumMuted)
                if (categories.isNotEmpty()) {
                    Text("也可以选择已有分类", color = AlbumMuted, style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { existing ->
                            SuggestionChip(onClick = { category = existing.name }, enabled = !busy, label = { Text(existing.name) })
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, category.ifBlank { "未分类" }) }, enabled = name.isNotBlank() && !busy) {
                Text(if (busy) "正在创建…" else "创建相册", color = AlbumCoral)
            }
        }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消", color = AlbumMuted) } })
}

// Keeps the name/category dialog accessible above the IME on short screens.
@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier
    .heightIn(max = 360.dp).verticalScroll(rememberScrollState()))

// Keep the selected original across recreation while the system file picker is open.
private val ExportPhotoSaver = listSaver<AlbumPhotoEntity?, String>(
    save = { photo -> photo?.let { listOf(it.id, it.albumId, it.localPath, it.mimeType,
        it.width.toString(), it.height.toString(), it.takenAtEpochMillis.toString(), it.importedAtEpochMillis.toString(),
        it.assetId.orEmpty(), it.addedBy.orEmpty(), it.cloudKnown.toString()) }.orEmpty() },
    restore = { values -> if (values.isEmpty()) null else AlbumPhotoEntity(values[0], values[1], values[2], values[3],
        values[4].toInt(), values[5].toInt(), values[6].toLong(), values[7].toLong(),
        assetId = values.getOrNull(8)?.takeIf { it.isNotBlank() },
        addedBy = values.getOrNull(9)?.takeIf { it.isNotBlank() }, cloudKnown = values.getOrNull(10)?.toBoolean() ?: false) },
)

@Composable
private fun AlbumPhotoViewer(photos: List<AlbumPhotoEntity>, index: Int, onClose: () -> Unit,
    onNavigate: (AlbumPhotoEntity) -> Unit, onCover: (AlbumPhotoEntity) -> Unit,
    canDelete: (AlbumPhotoEntity) -> Boolean, onDelete: (AlbumPhotoEntity) -> Unit) {
    val photo = photos[index]
    val context = LocalContext.current
    val exporter = remember(context) { AlbumPhotoExporter(context) }
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var exportNotice by remember(photo.id) { mutableStateOf<String?>(null) }
    var documentPhoto by rememberSaveable(stateSaver = ExportPhotoSaver) { mutableStateOf<AlbumPhotoEntity?>(null) }
    fun exportFailure(error: Exception): String = when (error) {
        is FileNotFoundException -> "照片无法读取，保存失败"
        is SecurityException -> "无法写入所选位置，请重新选择后重试"
        is IllegalArgumentException -> "这种照片格式暂时无法保存"
        else -> if (error.suppressed.isNotEmpty()) "保存失败，未完成的文件可能仍需手动删除"
            else "保存失败，请检查可用空间后重试"
    }
    fun copyPhoto(selected: AlbumPhotoEntity, destination: android.net.Uri? = null) {
        exporting = true
        exportNotice = null
        exportJob = scope.launch {
            try {
                if (destination == null) exporter.saveToMediaStore(selected)
                else exporter.saveToDocument(selected, destination)
                exportNotice = if (destination == null) "已保存到手机相册" else "已保存到所选位置"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                exportNotice = exportFailure(error)
            } finally {
                exporting = false
                exportJob = null
            }
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val selected = documentPhoto
        documentPhoto = null
        val destination = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && destination != null && selected != null) copyPhoto(selected, destination)
        else exportNotice = if (result.resultCode == Activity.RESULT_OK) "未获得保存位置，请重试" else "已取消保存"
    }
    fun savePhoto() {
        if (exporting || documentPhoto != null) return
        if (Build.VERSION.SDK_INT >= 29) copyPhoto(photo)
        else {
            exportNotice = null
            try {
                val intent = exporter.createDocumentIntent(photo)
                documentPhoto = photo
                documentPicker.launch(intent)
            } catch (_: Exception) {
                documentPhoto = null
                exportNotice = "无法打开文件保存器，请稍后重试"
            }
        }
    }
    val awaitingDocument = documentPhoto != null
    val busy = exporting || awaitingDocument
    val closeViewer: () -> Unit = {
        if (!awaitingDocument) {
            exportJob?.cancel()
            onClose()
        }
    }
    Dialog(onDismissRequest = closeViewer, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnBackPress = !awaitingDocument, dismissOnClickOutside = !awaitingDocument)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF191817)).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = closeViewer, enabled = !awaitingDocument) { Text("关闭", color = Color.White) }
                Text("${index + 1} / ${photos.size}", Modifier.weight(1f), color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { onCover(photo) }, enabled = !busy) { Text("设为封面", color = Color(0xFFF0B29E)) }
            }
            com.twomemory.designsystem.ZoomablePhoto(photo.id, Modifier.fillMaxWidth().weight(1f)) {
                AlbumLocalPhoto(photo, Modifier.fillMaxSize(), fit = true)
            }
            Text("双指缩放 · 双击缩放", Modifier.fillMaxWidth().padding(top = 8.dp),
                style = TwoMemoryTypography.caption, color = Color(0xFFAAA29A),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (canDelete(photo)) TextButton(onClick = { onDelete(photo) }, enabled = !busy,
                modifier = Modifier.align(Alignment.End).testTag("album-photo-delete")) {
                Text("移除照片", color = Color(0xFFF0B29E), style = MaterialTheme.typography.labelSmall)
            }
            val date = Instant.ofEpochMilli(photo.takenAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            Text("${date.year}年${date.monthValue}月${date.dayOfMonth}日", Modifier.fillMaxWidth().padding(12.dp),
                color = Color(0xFFD1C8BD), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            exportNotice?.let { message ->
                Text(message, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag("album-export-status").semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFF0B29E),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { onNavigate(photos[index - 1]) }, enabled = index > 0 && !busy) {
                    Text("上一张", color = if (index > 0) Color.White else AlbumMuted)
                }
                TextButton(onClick = ::savePhoto, enabled = !busy, modifier = Modifier.testTag("album-save-to-phone")) {
                    Text(if (exporting) "正在保存…" else if (awaitingDocument) "选择保存位置…" else "保存到手机",
                        color = Color(0xFFD1C8BD))
                }
                TextButton(onClick = { onNavigate(photos[index + 1]) }, enabled = index < photos.lastIndex && !busy) {
                    Text("下一张", color = if (index < photos.lastIndex) Color.White else AlbumMuted)
                }
            }
        }
    }
}

@Composable
internal fun AlbumLocalPhoto(photo: AlbumPhotoEntity, modifier: Modifier, fit: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    var visible by remember(photo.id, fit) { mutableStateOf(fit) }
    var transferError by remember(photo.id, fit) { mutableStateOf<String?>(null) }
    val previewPath = remember(photo.id, photo.assetId) {
        AlbumImageLoader.previewFile(context, photo, SyncSession.load(context))?.absolutePath
    }
    var bitmap by remember(photo.localPath, fit, photo.assetId) { mutableStateOf(
        AlbumPhotoPixels.cached(photo.localPath, fit)
            ?: previewPath?.let { AlbumPhotoPixels.cached(it, false) }) }
    var loading by remember(photo.localPath, fit) { mutableStateOf(true) }
    var awaitingDownload by remember(photo.localPath, fit) { mutableStateOf(false) }
    var reload by remember(photo.localPath, fit) { mutableIntStateOf(0) }
    LaunchedEffect(photo.localPath, photo.assetId, photo.importedAtEpochMillis, fit, reload, visible) {
        if (!visible) return@LaunchedEffect
        loading = true
        transferError = null
        val localExists = withContext(Dispatchers.IO) { java.io.File(photo.localPath).isFile }
        var verifiedPreview: ImageBitmap? = null
        if (fit && photo.assetId != null && !localExists) {
            verifiedPreview = withContext(Dispatchers.IO) {
                try {
                    val file = AlbumImageLoader.load(context, photo, original = false)
                    AlbumPhotoPixels.load(file.absolutePath, false)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
            // The original source is missing. Retain only pixels whose preview
            // source was validated, not a remembered detail from that old file.
            bitmap = verifiedPreview
        }
        val result = withContext(Dispatchers.IO) {
            // Cloud metadata arrives before its original. Missing remote cache is
            // pending, not a decoding failure; sync updates importedAt after commit.
            if (photo.assetId != null && !java.io.File(photo.localPath).isFile) {
                try {
                    val file = AlbumImageLoader.load(context, photo, original = fit)
                    AlbumPhotoPixels.load(file.absolutePath, fit) to false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    transferError = when ((error as? com.twomemory.network.MediaDownloadException)?.status) {
                        415 -> "点开查看原图"
                        404 -> "云端需更新，点击重试"
                        else -> "加载暂缓，点击重试"
                    }
                    null to true
                }
            } else try {
                AlbumPhotoPixels.load(photo.localPath, fit) to false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null to false }
        }
        // A null local decode is invalid, not a transient original transfer.
        // Only a failed transfer may keep this cycle's verified preview.
        bitmap = result.first ?: if (fit && result.second) verifiedPreview else null
        awaitingDownload = result.second
        loading = false
    }
    Box(modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        visible = fit || (bounds.width > 0 && bounds.height > 0 && bounds.bottom > 0 && bounds.top < view.height &&
            bounds.right > 0 && bounds.left < view.width)
    }.background(if (fit) Color(0xFF191817) else Color.Transparent), contentAlignment = Alignment.Center) {
        val shown = bitmap
        if (shown != null) Image(bitmap = shown, contentDescription = "相册照片", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize())
        else if (loading || awaitingDownload) Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (transferError == null) CircularProgressIndicator(Modifier.size(20.dp), color = AlbumCoral, strokeWidth = 2.dp)
            Text(if (transferError == "点开查看原图") "点开查看原图" else "加载中…",
                color = if (fit) Color.White else AlbumMuted, style = MaterialTheme.typography.labelSmall)
            transferError?.takeUnless { it == "点开查看原图" }?.let { message -> TextButton(onClick = { reload++ }) {
                Text(message, color = if (fit) Color.White else AlbumMuted, style = MaterialTheme.typography.labelSmall)
            } }
        }
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("照片无法读取", color = if (fit) Color.White else AlbumMuted, style = MaterialTheme.typography.labelSmall)
            if (fit) TextButton(onClick = { reload++ }) { Text("重试", color = Color(0xFFF0B29E)) }
        }
    }
}
