package com.twomemory.app

import android.content.Intent
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.twomemory.designsystem.EntryPhotos
import com.twomemory.editor.EditorPhoto
import com.twomemory.editor.EditorAttachment
import com.twomemory.model.EntryMode
import com.twomemory.model.BlockType
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** A provider-independent snapshot of content received from Android Sharesheet. */
data class IncomingShare(
    val title: String,
    val body: String,
    val imageUris: List<Uri>,
    val music: IncomingMusicShare? = null,
)

/**
 * Keeps the Sharesheet contract small and testable. The editor decides how to
 * present the draft; only explicitly supported provider song links are classified.
 */
object ShareIntentParser {
    fun parse(intent: Intent): IncomingShare? {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }
        val mimeType = intent.type.orEmpty()
        if (mimeType.isNotBlank() && !mimeType.startsWith("text/") && !mimeType.startsWith("image/")) {
            return null
        }
        @Suppress("DEPRECATION")
        val title = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty().trim()
        @Suppress("DEPRECATION")
        val body = (
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.joinToString("\n")
                ?: ""
            ).trim()
        val imageUris = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
            else -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::listOf).orEmpty()
            }
        }
        return if (title.isBlank() && body.isBlank() && imageUris.isEmpty()) {
            null
        } else {
            IncomingShare(title, body, imageUris, IncomingMusicParser.parse(listOf(title, body).joinToString("\n")))
        }
    }
}

/**
 * Receives system-shared text/images, copies provider bytes into app storage,
 * and hands the user to the normal personal editor. No network call happens
 * here: a share is a draft, and publishing remains explicit.
 */
class ShareReceiverActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var confirmation: AlertDialog? = null
    private var savedShare = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = ShareIntentParser.parse(intent)
        if (incoming == null) {
            openEditor()
            return
        }
        scope.launch {
            val photos = incoming.imageUris.take(MAX_SHARED_IMAGES).mapNotNull { uri ->
                val id = UUID.randomUUID()
                EntryPhotos.store(this@ShareReceiverActivity, uri, id.toString())?.let { stored ->
                    EditorPhoto(id = id, localPath = stored.localPath, mimeType = stored.mimeType)
                }
            }
            val music = incoming.music
            if (music == null) saveShare(incoming, photos)
            else confirmation = AlertDialog.Builder(this@ShareReceiverActivity)
                .setTitle("加入音乐分享？")
                .setMessage(listOf(music.title.ifBlank { "歌曲分享" }, music.artist, music.url)
                    .filter { it.isNotBlank() }.joinToString("\n"))
                .setPositiveButton("加入歌曲") { _, _ ->
                    val attachment = EditorAttachment(
                        id = UUID.randomUUID(), type = BlockType.MUSIC,
                        payload = JSONObject().put("title", music.title).put("artist", music.artist)
                            .put("url", music.url).put("source", music.source)
                            .put("originalText", incoming.body).toString(),
                    )
                    saveShare(incoming, photos, listOf(attachment))
                }
                .setNegativeButton("仅保留文字") { _, _ -> saveShare(incoming, photos) }
                .setOnCancelListener { saveShare(incoming, photos) }
                .show()
        }
    }

    private fun saveShare(
        incoming: IncomingShare,
        photos: List<EditorPhoto>,
        attachments: List<EditorAttachment> = emptyList(),
    ) {
        if (savedShare || isFinishing || isDestroyed) return
        savedShare = true
        DraftStore.mergeIncomingShare(
            context = incomingShareDraftContext(this),
            title = incoming.title,
            body = incoming.body,
            photos = photos,
            attachments = attachments,
        )
        openEditor()
    }

    private fun openEditor() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_EDITOR, EntryMode.PERSONAL.name)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        finish()
    }

    override fun onDestroy() {
        confirmation?.setOnCancelListener(null)
        confirmation?.dismiss()
        confirmation = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val MAX_SHARED_IMAGES = 6
    }
}
