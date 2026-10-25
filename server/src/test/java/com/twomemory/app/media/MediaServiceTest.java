package com.twomemory.app.media;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaServiceTest {

    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void cloudAlbumAcceptsTheSameSixtyFourMiBOriginalLimitAndRasterFormatsAsLocalImport() {
        for (String mime : java.util.List.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "image/avif", "image/gif", "image/bmp", "image/vnd.wap.wbmp")) {
            MediaService.validate(new CreateMediaCommand(USER, SPACE, MediaKind.IMAGE, mime,
                    64L * 1024 * 1024, 1200, 900, null, "a".repeat(64), UUID.randomUUID()));
        }
    }

    @Test
    void imageUploadRequiresWhitelistedMimePositiveDimensionsAndSixtyFourMiBLimit() {
        CreateMediaCommand valid = new CreateMediaCommand(
                USER, SPACE, MediaKind.IMAGE, "image/jpeg", 1024, 1200, 900,
                null, "a".repeat(64), UUID.randomUUID());
        MediaService.validate(valid);
        assertThat(MediaService.objectKey(valid, UUID.fromString("00000000-0000-0000-0000-000000000020"),
                java.time.Instant.parse("2026-09-30T16:00:00Z")))
                .isEqualTo("couples/00000000-0000-0000-0000-000000000010/records/images/2026-10/00000000-0000-0000-0000-000000000020.jpg");

        assertThatThrownBy(() -> MediaService.validate(valid.withMimeType("application/octet-stream")))
                .isInstanceOf(MediaValidationException.class);
        assertThatThrownBy(() -> MediaService.validate(valid.withByteSize(64L * 1024 * 1024 + 1)))
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

    @Test
    void videoAndAudioUseTheSameVerifiedMediaTicketFlow() {
        MediaService.validate(new CreateMediaCommand(
                USER, SPACE, MediaKind.VIDEO, "video/mp4", 1024, 1, 1,
                12_000L, "a".repeat(64), UUID.randomUUID()));
        MediaService.validate(new CreateMediaCommand(
                USER, SPACE, MediaKind.AUDIO, "audio/mpeg", 1024, 1, 1,
                12_000L, "b".repeat(64), UUID.randomUUID()));
    }

    @Test
    void completionRejectsChecksumMismatchBeforeReadingObjectStore() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        com.twomemory.app.auth.SpaceAccessPolicy accessPolicy = mock(com.twomemory.app.auth.SpaceAccessPolicy.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        MediaService service = new MediaService(jdbcTemplate, accessPolicy, objectStorage);
        UUID assetId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        MediaAssetView asset = new MediaAssetView(
                assetId, SPACE, USER, MediaKind.IMAGE, MediaStatus.UPLOADING,
                "couples/space/assets/image", "image/png", 4, 1, 1,
                null, "a".repeat(64), null);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(assetId)))
                .thenReturn(asset);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), eq(assetId)))
                .thenReturn(asset);

        assertThatThrownBy(() -> service.completeUpload(USER, assetId, "b".repeat(64)))
                .isInstanceOf(MediaValidationException.class)
                .hasMessageContaining("checksum");
        verify(objectStorage, never()).head(asset.objectKey());
        verify(jdbcTemplate).update("UPDATE media_asset SET status = 'FAILED', updated_at = now() WHERE id = ?", assetId);
    }
}
