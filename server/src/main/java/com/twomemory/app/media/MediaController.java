package com.twomemory.app.media;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;
    private final ObjectStorage objectStorage;
    // Small bounded previews only, never original bytes. Authorization runs before every cache hit.
    private record ThumbnailKey(UUID assetId, int maxEdge) {}
    private final java.util.Map<ThumbnailKey, byte[]> thumbnails = new java.util.LinkedHashMap<>(32, .75f, true);
    private final java.util.concurrent.Semaphore thumbnailGeneration = new java.util.concurrent.Semaphore(1);
    private int thumbnailBytes;

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
            jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        MediaAssetView asset = mediaService.readAsset(actor.userId(), assetId);
        if (!actor.userId().equals(asset.ownerId())) throw new MediaAccessDeniedException("only the uploader may write this asset");
        if(request.getContentLengthLong()>asset.byteSize()) throw new MediaValidationException("body exceeds upload ticket");
        if(asset.status()==MediaStatus.READY) {
            // Lost ACK retries are valid, but a completed original is immutable.
            try(var input=request.getInputStream()) { objectStorage.verifyInput(input,asset.byteSize(),asset.sha256()); }
            return asset;
        }
        try(var input=request.getInputStream()) { objectStorage.putVerified(asset.objectKey(),input,asset.byteSize(),asset.sha256(),asset.mimeType()); }
        return mediaService.completeUpload(actor.userId(), assetId, asset.sha256());
    }

    /** Serves stored bytes to any authorized member of the owning space. */
    @GetMapping("/{assetId}/data")
    public ResponseEntity<org.springframework.core.io.Resource> downloadData(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID assetId) {
        MediaAssetView asset = mediaService.readAsset(actor.userId(), assetId);
        var resource=new org.springframework.core.io.InputStreamResource(() -> objectStorage.open(asset.objectKey()),"private media original");
        return ResponseEntity.ok()
                .header("Cache-Control", "private, no-store")
                .contentType(MediaType.parseMediaType(asset.mimeType()))
                .contentLength(asset.byteSize())
                .body(resource);
    }

    @GetMapping("/{assetId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID assetId,
            @RequestParam(name = "maxEdge", defaultValue = "600") int maxEdge) {
        MediaAssetView asset = mediaService.readAsset(actor.userId(), assetId);
        if (maxEdge != 600 && maxEdge != 1200) return ResponseEntity.badRequest().build();
        if (asset.kind() != MediaKind.IMAGE || asset.status() != MediaStatus.READY)
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        byte[] bytes;
        ThumbnailKey key = new ThumbnailKey(assetId, maxEdge);
        // Cache hits never wait for storage I/O or decoding on a different cold preview.
        synchronized (thumbnails) {
            bytes = thumbnails.get(key);
        }
        if (bytes == null) {
            // Only one original read/decode at a time on the 2GiB server, outside the cache monitor.
            try { thumbnailGeneration.acquire(); }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            try {
                synchronized (thumbnails) { bytes = thumbnails.get(key); }
                if (bytes == null) {
                    try { bytes = ImageThumbnail.render(objectStorage.read(asset.objectKey()), maxEdge); }
                    catch (UnsupportedOperationException exception) { return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build(); }
                    catch (java.io.IOException exception) { return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build(); }
                    synchronized (thumbnails) {
                        thumbnails.put(key, bytes); thumbnailBytes += bytes.length;
                        while (thumbnailBytes > 8 * 1024 * 1024 || thumbnails.size() > 128) {
                            var first = thumbnails.entrySet().iterator(); var removed = first.next();
                            thumbnailBytes -= removed.getValue().length; first.remove();
                        }
                    }
                }
            } finally { thumbnailGeneration.release(); }
        }
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .contentType(MediaType.IMAGE_JPEG).contentLength(bytes.length).body(bytes);
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
