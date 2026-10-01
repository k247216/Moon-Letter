package com.twomemory.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.twomemory.designsystem.EntryPhotos
import com.twomemory.editor.EditorPhoto
import com.twomemory.model.EntryMode
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
)

/**
 * Keeps the Sharesheet contract small and testable. The editor decides how to
 * present the draft; this parser never guesses a media type from a URL.
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
            IncomingShare(title, body, imageUris)
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
            DraftStore.mergeIncomingShare(
                context = this@ShareReceiverActivity,
                title = incoming.title,
                body = incoming.body,
                photos = photos,
            )
            openEditor()
        }
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
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val MAX_SHARED_IMAGES = 6
    }
}
