package com.twomemory.app.media;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaServiceTest {

    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void imageUploadRequiresWhitelistedMimePositiveDimensionsAndTwentyMiBLimit() {
        CreateMediaCommand valid = new CreateMediaCommand(
                USER, SPACE, MediaKind.IMAGE, "image/jpeg", 1024, 1200, 900,
                null, "a".repeat(64), UUID.randomUUID());
        MediaService.validate(valid);
        assertThat(MediaService.objectKey(SPACE, UUID.fromString("00000000-0000-0000-0000-000000000020")))
                .isEqualTo("couples/00000000-0000-0000-0000-000000000010/assets/00000000-0000-0000-0000-000000000020");

        assertThatThrownBy(() -> MediaService.validate(valid.withMimeType("application/octet-stream")))
                .isInstanceOf(MediaValidationException.class);
        assertThatThrownBy(() -> MediaService.validate(valid.withByteSize(20L * 1024 * 1024 + 1)))
                .isInstanceOf(MediaValidationException.class);
        assertThatThrownBy(() -> MediaService.validate(valid.withWidth(0)))
                .isInstanceOf(MediaValidationException.class);
    }

    @Test
    void sha256MustBeLowercaseHex() {
        assertThatThrownBy(() -> MediaService.validate(new CreateMediaCommand(
                USER, SPACE, MediaKind.IMAGE, "image/png", 10, 1, 1,
                null, "not-a-hash", UUID.randomUUID())))
                .isInstanceOf(MediaValidationException.class);
    }
}
