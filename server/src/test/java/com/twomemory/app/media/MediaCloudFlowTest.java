package com.twomemory.app.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.twomemory.app.auth.AuthenticatedUser;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class MediaCloudFlowTest {

    private static final UUID COUPLE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OWNER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ASSET_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void s3ModeReturnsSignedUploadTicketWithoutExposingRawUploadEndpoint() {
        MediaService mediaService = mock(MediaService.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        MediaController controller = new MediaController(mediaService, objectStorage);
        AuthenticatedUser actor = new AuthenticatedUser(OWNER_ID, COUPLE_ID, UUID.randomUUID());
        UploadTicket ticket = new UploadTicket(
                ASSET_ID,
                "media/cccccccc-cccc-cccc-cccc-cccccccccccc/image.webp",
                "https://oss.example.com/moon-letter/media/image.webp?signature=test",
                Instant.parse("2026-10-02T01:10:00Z"),
                new MediaAssetView(
                        ASSET_ID,
                        COUPLE_ID,
                        OWNER_ID,
                        MediaKind.IMAGE,
                        MediaStatus.UPLOADING,
                        "media/cccccccc-cccc-cccc-cccc-cccccccccccc/image.webp",
                        "image/webp",
                        128,
                        1,
                        1,
                        null,
                        "sha256",
                        null));
        when(mediaService.createUpload(any(CreateMediaCommand.class))).thenReturn(ticket);

        ResponseEntity<UploadTicket> response = controller.createUpload(
                actor,
                new CreateMediaCommand(
                        OWNER_ID,
                        COUPLE_ID,
                        MediaKind.IMAGE,
                        "image/webp",
                        128,
                        1,
                        1,
                        null,
                        "sha256",
                        UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(ticket);
        assertThat(response.getBody().uploadUrl()).startsWith("https://");
        verify(mediaService).createUpload(any(CreateMediaCommand.class));
        verifyNoInteractions(objectStorage);
    }

    @Test
    void authorizedMemberCanReadPrivateS3ObjectThroughMediaEndpoint() throws Exception {
        MediaService mediaService = mock(MediaService.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        MediaController controller = new MediaController(mediaService, objectStorage);
        AuthenticatedUser actor = new AuthenticatedUser(OWNER_ID, COUPLE_ID, UUID.randomUUID());
        byte[] image = new byte[] {1, 2, 3, 4};
        when(mediaService.readAsset(actor.userId(), ASSET_ID)).thenReturn(readyAsset());
        when(objectStorage.open("media/cccccccc-cccc-cccc-cccc-cccccccccccc/image.webp"))
                .thenReturn(new java.io.ByteArrayInputStream(image));

        var response = controller.downloadData(actor, ASSET_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try(var stream=response.getBody().getInputStream()) { assertThat(stream.readAllBytes()).containsExactly(1, 2, 3, 4); }
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.valueOf("image/webp"));
    }

    @Test
    void unauthorizedMemberIsRejectedBeforePrivateObjectIsRead() {
        MediaService mediaService = mock(MediaService.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        MediaController controller = new MediaController(mediaService, objectStorage);
        AuthenticatedUser actor = new AuthenticatedUser(OWNER_ID, COUPLE_ID, UUID.randomUUID());
        MediaAccessDeniedException denied = new MediaAccessDeniedException("member cannot read asset");
        when(mediaService.readAsset(actor.userId(), ASSET_ID)).thenThrow(denied);

        assertThatThrownBy(() -> controller.downloadData(actor, ASSET_ID))
                .isSameAs(denied);
        verifyNoInteractions(objectStorage);
    }

    private static MediaAssetView readyAsset() {
        return new MediaAssetView(
                ASSET_ID,
                COUPLE_ID,
                OWNER_ID,
                MediaKind.IMAGE,
                MediaStatus.READY,
                "media/cccccccc-cccc-cccc-cccc-cccccccccccc/image.webp",
                "image/webp",
                4,
                1,
                1,
                null,
                "sha256",
                null);
    }
}
