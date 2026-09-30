package com.twomemory.app.entry;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/entries")
public class EntryController {

    private final EntryService entryService;
    private final CommentService commentService;

    public EntryController(EntryService entryService, CommentService commentService) {
        this.entryService = entryService;
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<EntryView> createDraft(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestBody CreateEntryCommand command) {
        UUID userId = AuthenticatedUser.fromHeader(rawUserId).userId();
        if (!userId.equals(command.authorId())) {
            throw new EntryAccessDeniedException("entry author must be the authenticated user");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(entryService.createDraft(command));
    }

    @GetMapping("/{entryId}")
    public EntryView readEntry(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @PathVariable UUID entryId) {
        return entryService.readEntry(entryId, AuthenticatedUser.fromHeader(rawUserId).userId());
    }

    @PostMapping("/{entryId}/publish")
    public PublishResult publish(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @PathVariable UUID entryId,
            @RequestBody PublishRequest request) {
        AuthenticatedUser.fromHeader(rawUserId);
        return entryService.publish(entryId, request.baseVersion());
    }

    @PostMapping("/{entryId}/changes")
    public ApplyChangesResult applyChanges(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @PathVariable UUID entryId,
            @RequestBody ApplyChangesRequest request) {
        UUID userId = AuthenticatedUser.fromHeader(rawUserId).userId();
        if (request.mutations() != null && request.mutations().stream()
                .anyMatch(mutation -> !userId.equals(mutation.authorId()))) {
            throw new EntryAccessDeniedException("block author must be the authenticated user");
        }
        return entryService.applyChanges(entryId, request.baseRevision(), request.mutations());
    }

    @PostMapping("/{entryId}/resolve")
    public EntryView resolveConflict(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @PathVariable UUID entryId,
            @RequestBody ResolveConflictCommand command) {
        UUID userId = AuthenticatedUser.fromHeader(rawUserId).userId();
        if (command.mutations() != null && command.mutations().stream()
                .anyMatch(mutation -> !userId.equals(mutation.authorId()))) {
            throw new EntryAccessDeniedException("block author must be the authenticated user");
        }
        return entryService.resolveConflict(entryId, command);
    }

    @PostMapping("/{entryId}/comments")
    public CommentView addComment(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @PathVariable UUID entryId,
            @RequestBody CommentRequest request) {
        UUID userId = AuthenticatedUser.fromHeader(rawUserId).userId();
        return commentService.addComment(entryId, userId, request.body(), request.replyToId());
    }

    @ExceptionHandler(EntryConflict.class)
    ResponseEntity<ApiError> conflict(EntryConflict exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("ENTRY_CONFLICT", exception.getMessage(), exception.entryId(),
                        exception.conflictingBlockIds()));
    }

    @ExceptionHandler(EntryAccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(EntryAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", exception.getMessage(), null, null));
    }

    @ExceptionHandler(EntryValidationException.class)
    ResponseEntity<ApiError> badRequest(EntryValidationException exception) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", exception.getMessage(), null, null));
    }

    record ApiError(String code, String message, UUID entryId, java.util.List<UUID> conflictingBlockIds) {
    }
}
