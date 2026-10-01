package com.twomemory.app

/**
 * The address a phone talks to, resolved from what this device was told.
 */
object ServerAddress {

    /** Development-machine convenience only; see [resolve]. */
    const val DEV_DEFAULT_BASE_URL = "http://10.138.79.194:8080"

    /**
     * The address that was stored wins. Only a debug build may fall back to the
     * development machine, and a release build with nothing stored gets `null`
     * so the app shows the binding screen and asks, rather than silently
     * looking for a server that is not hers.
     */
    fun resolve(stored: String?, isDebugBuild: Boolean): String? =
        stored?.takeIf { it.isNotBlank() }
            ?: (if (isDebugBuild) DEV_DEFAULT_BASE_URL else null)?.takeIf { it.isNotBlank() }
}
