package com.twomemory.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    /**
     * Set when a notice was tapped. A state, not a saved-instance value: the same
     * activity handles a notice while the app is already open (onNewIntent), and
     * the navigation layer has to recompose for it.
     */
    var openEntryId by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openEntryId = intent?.getStringExtra(EXTRA_OPEN_ENTRY)
        setContent {
            TwoMemoryApp(initialEntryId = openEntryId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openEntryId = intent.getStringExtra(EXTRA_OPEN_ENTRY)
    }

    companion object {
        const val EXTRA_OPEN_ENTRY = "com.twomemory.app.OPEN_ENTRY"
    }
}
