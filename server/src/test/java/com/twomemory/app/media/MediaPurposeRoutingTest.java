package com.twomemory.app.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Exercise the real ticket creation boundary; existing operation/path rows must stay untouched. */
class MediaPurposeRoutingTest {
    private final ObjectMapper json = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final UUID space = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private final UUID user = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void ticketsUseBusinessPurposeAndCorrectExtension() throws Exception {
        for (var row : new String[][] {
                {"AVATAR", "IMAGE", "image/png", "avatars", "png"},
                {"ALBUM", "IMAGE", "image/jpeg", "albums/photos", "jpg"},
                {"RECORD", "IMAGE", "image/heic", "records/images", "heic"},
                {"RECORD", "AUDIO", "audio/mp4", "records/audio", "m4a"},
                {"RECORD", "VIDEO", "video/quicktime", "records/video", "mov"}}) {
            var service = new MediaService(mock(JdbcTemplate.class),
                    mock(com.twomemory.app.auth.SpaceAccessPolicy.class), new LocalObjectStorage("target/purpose-routing-test"));
            var body = new java.util.HashMap<String, Object>(Map.of(
                    "ownerId", user.toString(), "coupleId", space.toString(), "kind", row[1],
                    "mimeType", row[2], "byteSize", 4, "width", 1, "height", 1,
                    "sha256", "a".repeat(64), "operationId", UUID.randomUUID().toString()));
            body.put("purpose", row[0]);
            var command = json.readValue(json.writeValueAsString(body), CreateMediaCommand.class);
            var ticket = service.createUpload(command);
            assertThat(ticket.objectKey()).matches("couples/" + space + "/" + row[3]
                    + "/[0-9]{4}-[0-9]{2}/" + ticket.assetId() + "\\." + row[4]);
        }
    }
}
