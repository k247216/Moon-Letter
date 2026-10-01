package com.twomemory.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which server a phone talks to is the difference between "my records" and "a
 * spinner". The development machine's LAN address is only ever a convenience
 * for a debug build: a release carrying it would send her phone looking for a
 * network that does not exist at her place, and the failure would look like
 * lost data rather than like a wrong address.
 */
class ServerAddressTest {

    @Test
    fun theStoredAddressWinsBecauseSheTypedIt() {
        assertEquals(
            "http://192.168.1.50:8080",
            ServerAddress.resolve("http://192.168.1.50:8080", isDebugBuild = true),
        )
        assertEquals(
            "http://192.168.1.50:8080",
            ServerAddress.resolve("http://192.168.1.50:8080", isDebugBuild = false),
        )
    }

    @Test
    fun aDebugBuildFallsBackToTheDevelopmentMachine() {
        assertEquals(
            ServerAddress.DEV_DEFAULT_BASE_URL,
            ServerAddress.resolve(null, isDebugBuild = true),
        )
        assertEquals(
            ServerAddress.DEV_DEFAULT_BASE_URL,
            ServerAddress.resolve("   ", isDebugBuild = true),
        )
    }

    @Test
    fun aReleaseBuildWithoutAnAddressHasNoServerInsteadOfAGuessedOne() {
        assertNull(ServerAddress.resolve(null, isDebugBuild = false))
        assertNull(ServerAddress.resolve("", isDebugBuild = false))
    }
}
