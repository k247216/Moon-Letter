package com.twomemory.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaPurposeSerializationTest {
    @Test fun legacyRecordUploadExplicitlyIdentifiesItsPurpose() {
        val command = MediaCreateCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "IMAGE", "image/png", 4, 1, 1, "a".repeat(64))
        val payload = JsonParser.parseString(Gson().toJson(command)).asJsonObject
        assertEquals("RECORD", payload.get("purpose")?.asString)
    }
    @Test fun albumAndAvatarUploadsKeepTheirExplicitPurposeOnTheWire() {
        for (purpose in listOf("ALBUM", "AVATAR")) {
            val command = MediaCreateCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "IMAGE", "image/png", 4, 1, 1, "a".repeat(64), purpose = purpose)
            val payload = JsonParser.parseString(Gson().toJson(command)).asJsonObject
            assertEquals(purpose, payload.get("purpose").asString)
        }
    }
}
