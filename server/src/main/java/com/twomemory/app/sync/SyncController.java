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

    /** No feed row was written, so there is nothing to tell the other device. */
    private static final long NO_CHANGE_ROW = -1L;

    private final IdempotencyService idempotencyService;
    private final ChangeFeedService changeFeedService;
    private final SyncOperationDispatcher operationDispatcher;
    private final SyncNotificationPublisher notificationPublisher;
    private final SpaceAccessPolicy accessPolicy;

    public SyncController(IdempotencyService idempotencyService,
                           ChangeFeedService changeFeedService,
                           SyncOperationDispatcher operationDispatcher,
                           SyncNotificationPublisher notificationPublisher,
                           SpaceAccessPolicy accessPolicy) {
        this.idempotencyService = idempotencyService;
        this.changeFeedService = changeFeedService;
        this.operationDispatcher = operationDispatcher;
        this.notificationPublisher = notificationPublisher;
        this.accessPolicy = accessPolicy;
    }

    @PostMapping("/operations")
    public ResponseEntity<MutationResult> executeOperation(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestBody SyncOperationRequest request) {
        UUID userId = actor.userId();
        if (request.coupleId() == null || request.operationId() == null) {
            throw new SyncValidationException("operation id and couple id are required");
        }
        accessPolicy.requireMember(userId, request.coupleId());
        if (!SyncPayloadHasher.hash(request).equalsIgnoreCase(request.payloadHash())) {
            throw new SyncValidationException("payload hash does not match canonical operation payload");
        }
        AtomicLong sequence = new AtomicLong(NO_CHANGE_ROW);
        MutationResult result = idempotencyService.executeOnce(
                request.operationId(), userId, request.payloadHash(), () -> {
                    SyncOperationDispatcher.DispatchOutcome outcome = operationDispatcher.dispatch(
                            userId, request.coupleId(), request.operationType(), request.payload());
                    long latest = switch (outcome.changeRow()) {
                        case APPENDED_BY_MUTATION -> changeFeedService.lastSequence(request.coupleId());
                        case APPENDED_BY_CONTROLLER -> changeFeedService.appendChange(
                                request.coupleId(), outcome.entityType(), outcome.entityId(),
                                outcome.operation(), outcome.responseBody());
                        case SUPPRESSED -> NO_CHANGE_ROW;
                    };
                    sequence.set(latest);
                    return new MutationResult(200, outcome.responseBody(), false);
                });
        if (!result.replayed() && sequence.get() != NO_CHANGE_ROW) {
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

    @ExceptionHandler(com.twomemory.app.entry.EntryAccessDeniedException.class)
    ResponseEntity<SyncError> forbidden(com.twomemory.app.entry.EntryAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new SyncError("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(com.twomemory.app.entry.EntryConflict.class)
    ResponseEntity<SyncError> entryConflict(com.twomemory.app.entry.EntryConflict exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new SyncError("ENTRY_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(com.twomemory.app.entry.EntryValidationException.class)
    ResponseEntity<SyncError> entryValidation(com.twomemory.app.entry.EntryValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new SyncError("ENTRY_VALIDATION_ERROR", exception.getMessage()));
    }

    record SyncError(String code, String message) {
    }
}
