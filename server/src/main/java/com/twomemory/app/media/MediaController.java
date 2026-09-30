package com.twomemory.app.media;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/uploads")
    public ResponseEntity<UploadTicket> createUpload(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestBody CreateMediaCommand command) {
        UUID userId = AuthenticatedUser.fromHeader(rawUserId).userId();
        if (!userId.equals(command.ownerId())) {
            throw new MediaAccessDeniedException("media owner must be the authenticated user");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.createUpload(command));
    }

    @PostMapping("/{assetId}/complete")
    public MediaAssetView completeUpload(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @PathVariable UUID assetId,
            @RequestBody CompleteUploadRequest request) {
        return mediaService.completeUpload(
                AuthenticatedUser.fromHeader(rawUserId).userId(), assetId, request.sha256());
    }

    @PostMapping("/{assetId}/failed")
    public MediaAssetView markFailed(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @PathVariable UUID assetId,
            @RequestBody FailureRequest request) {
        return mediaService.markFailed(
                AuthenticatedUser.fromHeader(rawUserId).userId(), assetId, request.code());
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
