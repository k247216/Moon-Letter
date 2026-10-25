package com.twomemory.app

/**
 * One Activity's initial-frame decision. All timestamps are monotonic milliseconds.
 * Navigation signals readiness after its first local timeline and async Decor
 * settle, or calls release for setup/error/direct destinations. No network input,
 * saved state, preference, or process-wide singleton belongs to this gate.
 */
internal class StartupFrameGate(
    private val startedAtMillis: Long,
    private val maxWaitMillis: Long = 1_000L,
) {
    private var released = false

    fun onInitialContentReady() = release()

    fun release() {
        released = true
    }

    fun shouldKeepOnScreen(nowMillis: Long): Boolean {
        if (!released && nowMillis - startedAtMillis >= maxWaitMillis.coerceIn(0L, 1_000L)) {
            release()
        }
        return !released
    }
}
