package com.twomemory.app.sync;

import com.twomemory.app.auth.AuthenticatedUser;
import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final IdempotencyService idempotencyService;
    private final ChangeFeedService changeFeedService;
    private final SyncNotificationPublisher notificationPublisher;
    private final SpaceAccessPolicy accessPolicy;

    public SyncController(IdempotencyService idempotencyService,
                           ChangeFeedService changeFeedService,
                           SyncNotificationPublisher notificationPublisher,
                           SpaceAccessPolicy accessPolicy) {
        this.idempotencyService = idempotencyService;
        this.changeFeedService = changeFeedService;
        this.notificationPublisher = notificationPublisher;
        this.accessPolicy = accessPolicy;
    }

    @PostMapping("/operations")
    public ResponseEntity<MutationResult> executeOperation(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestBody SyncOperationRequest request) {
        UUID userId = actor.userId();
        accessPolicy.requireMember(userId, request.coupleId());
        if (!SyncPayloadHasher.hash(request).equalsIgnoreCase(request.payloadHash())) {
            throw new SyncValidationException("payload hash does not match canonical operation payload");
        }
        AtomicLong sequence = new AtomicLong(-1);
        MutationResult result = idempotencyService.executeOnce(
                request.operationId(), userId, request.payloadHash(), () -> {
                    long latest = changeFeedService.appendChange(
                            request.coupleId(), request.entityType(), request.entityId(),
                            request.operation(), request.body());
                    sequence.set(latest);
                    return new MutationResult(200, request.body(), false);
                });
        if (!result.replayed() && sequence.get() >= 0) {
            notificationPublisher.notifySpaceChanged(request.coupleId(), sequence.get());
        }
        return ResponseEntity.status(result.status()).body(result);
    }

    @GetMapping("/changes")
    public ChangePage readChanges(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam UUID coupleId,
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "50") int limit) {
        return changeFeedService.readChanges(actor.userId(), coupleId, after, limit);
    }

    @ExceptionHandler(SyncConflictException.class)
    ResponseEntity<SyncError> conflict(SyncConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new SyncError("SYNC_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(SyncValidationException.class)
    ResponseEntity<SyncError> badRequest(SyncValidationException exception) {
        return ResponseEntity.badRequest().body(new SyncError("SYNC_VALIDATION_ERROR", exception.getMessage()));
    }

    record SyncError(String code, String message) {
    }
}
