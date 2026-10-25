package com.twomemory.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twomemory.app.notifications.NoticeTargetScope

class MainActivity : ComponentActivity() {

    // Activity-local and one-shot: never saved across recreation or account changes.
    private var startupFrameGate: StartupFrameGate? = null
    private var startupView: View? = null
    private var startupPreDraw: ViewTreeObserver.OnPreDrawListener? = null
    private val startupHandler = Handler(Looper.getMainLooper())
    private val startupTimeout = Runnable { releaseStartupFrame() }

    /**
     * Set when a notice was tapped. A state, not a saved-instance value: the same
     * activity handles a notice while the app is already open (onNewIntent), and
     * the navigation layer has to recompose for it.
     */
    var openEntryId by mutableStateOf<String?>(null)
        private set
    private var openReminder by mutableStateOf<String?>(null)
    private var noticeScope by mutableStateOf<String?>(null)
    private var noticeRequest by mutableStateOf(0)

    /** Incremented for every Sharesheet hand-off, including while the app is open. */
    var openEditorRequest by mutableStateOf(0)
        private set
    var openEditorMode by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        val startedAtMillis = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        acceptNoticeIntent(intent)
        acceptEditorIntent(intent)
        installStartupFrameGate(startedAtMillis)
        setContent {
            TwoMemoryApp(
                initialEntryId = openEntryId,
                initialEditorMode = openEditorMode,
                initialEditorRequest = openEditorRequest,
                onInitialContentReady = ::releaseStartupFrame,
                initialReminder = openReminder,
                initialNoticeScope = noticeScope,
                initialNoticeRequest = noticeRequest,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptNoticeIntent(intent)
        acceptEditorIntent(intent)
        if (openEntryId != null || openReminder != null || openEditorMode != null) releaseStartupFrame()
    }

    private fun installStartupFrameGate(startedAtMillis: Long) {
        val gate = StartupFrameGate(startedAtMillis)
        startupFrameGate = gate
        if (openEntryId != null || openReminder != null || openEditorMode != null) {
            gate.release()
            return
        }
        val view = window.decorView
        val listener = ViewTreeObserver.OnPreDrawListener {
            val keep = gate.shouldKeepOnScreen(SystemClock.uptimeMillis())
            if (!keep) releaseStartupFrame()
            !keep
        }
        startupView = view
        startupPreDraw = listener
        view.viewTreeObserver.addOnPreDrawListener(listener)
        // The timer also requests a new draw if no local/Decor callback arrives.
        // Every pre-draw checks the same deadline, independent of timer scheduling.
        startupHandler.postAtTime(startupTimeout, startedAtMillis + 1_000L)
    }

    private fun releaseStartupFrame() {
        startupFrameGate?.release()
        startupHandler.removeCallbacks(startupTimeout)
        val view = startupView
        val listener = startupPreDraw
        if (view != null && listener != null && view.viewTreeObserver.isAlive) {
            view.viewTreeObserver.removeOnPreDrawListener(listener)
        }
        startupPreDraw = null
        startupView = null
        view?.postInvalidateOnAnimation()
    }

    override fun onDestroy() {
        releaseStartupFrame()
        super.onDestroy()
    }

    private fun acceptEditorIntent(intent: Intent?) {
        val mode = intent?.getStringExtra(EXTRA_OPEN_EDITOR) ?: return
        openEditorMode = mode
        openEditorRequest += 1
    }

    private fun acceptNoticeIntent(intent: Intent?) {
        openEntryId = null
        openReminder = null
        noticeScope = null
        if (!NoticeTargetScope.accepts(this, intent)) return
        openEntryId = intent?.getStringExtra(EXTRA_OPEN_ENTRY)
        openReminder = intent?.getStringExtra(EXTRA_OPEN_REMINDER)
            ?.takeIf { it in setOf("anniversary", "occasion", "capsule") }
        noticeScope = intent?.getStringExtra(NoticeTargetScope.EXTRA)
        if (openEntryId != null || openReminder != null) noticeRequest += 1
    }

    companion object {
        const val EXTRA_OPEN_ENTRY = "com.twomemory.app.OPEN_ENTRY"
        const val EXTRA_OPEN_EDITOR = "com.twomemory.app.OPEN_EDITOR"
        const val EXTRA_OPEN_REMINDER = "com.twomemory.app.OPEN_REMINDER"
        const val EXTRA_REMINDER_ID = "com.twomemory.app.REMINDER_ID"
    }
}
