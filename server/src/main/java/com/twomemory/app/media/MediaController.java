package com.twomemory.app.media;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;
    private final ObjectStorage objectStorage;

    public MediaController(MediaService mediaService, ObjectStorage objectStorage) {
        this.mediaService = mediaService;
        this.objectStorage = objectStorage;
    }

    @PostMapping("/uploads")
    public ResponseEntity<UploadTicket> createUpload(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestBody CreateMediaCommand command) {
        if (!actor.userId().equals(command.ownerId())) {
            throw new MediaAccessDeniedException("media owner must be the authenticated user");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.createUpload(command));
    }

    @PostMapping("/{assetId}/complete")
    public MediaAssetView completeUpload(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID assetId,
            @RequestBody CompleteUploadRequest request) {
        return mediaService.completeUpload(actor.userId(), assetId, request.sha256());
    }

    @PostMapping("/{assetId}/failed")
    public MediaAssetView markFailed(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID assetId,
            @RequestBody FailureRequest request) {
        return mediaService.markFailed(actor.userId(), assetId, request.code());
    }

    /**
     * Local-storage raw upload: the client streams bytes here, the server
     * writes them into the object store, verifies checksum and size, and
     * marks the asset READY in one step.
     */
    @PostMapping("/{assetId}/data")
    public MediaAssetView uploadData(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID assetId,
            @RequestBody byte[] bytes) {
        MediaAssetView asset = mediaService.readAsset(actor.userId(), assetId);
        if (!(objectStorage instanceof LocalObjectStorage local)) {
            throw new MediaValidationException("raw upload is only available in local storage mode");
        }
        local.put(asset.objectKey(), bytes);
        return mediaService.completeUpload(actor.userId(), assetId, local.sha256(bytes));
    }

    /** Serves stored bytes to any member of the owning space. */
    @GetMapping("/{assetId}/data")
    public ResponseEntity<byte[]> downloadData(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID assetId) {
        MediaAssetView asset = mediaService.readAsset(actor.userId(), assetId);
        if (!(objectStorage instanceof LocalObjectStorage local)) {
            throw new MediaValidationException("raw download is only available in local storage mode");
        }
        byte[] bytes = local.read(asset.objectKey());
        return ResponseEntity.ok()
                .header("Content-Type", asset.mimeType())
                .header("Content-Length", String.valueOf(bytes.length))
                .body(bytes);
    }

    @ExceptionHandler(MediaValidationException.class)
    ResponseEntity<MediaError> validation(MediaValidationException exception) {
        return ResponseEntity.badRequest().body(new MediaError("MEDIA_VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(MediaAccessDeniedException.class)
    ResponseEntity<MediaError> forbidden(MediaAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MediaError("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(MediaNotReadyException.class)
    ResponseEntity<MediaError> notReady(MediaNotReadyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MediaError("MEDIA_NOT_READY", exception.getMessage()));
    }

    record FailureRequest(String code) {
    }

    record MediaError(String code, String message) {
    }
}
