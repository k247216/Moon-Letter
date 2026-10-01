package com.twomemory.app

import android.app.Application

/**
 * WorkManager can start a sync or a weekly review with no activity ever having
 * run, so the pipeline and the notices are wired here instead of from the UI.
 */
class MoonLetterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncSession.installProcessHooks(this)
    }
}
