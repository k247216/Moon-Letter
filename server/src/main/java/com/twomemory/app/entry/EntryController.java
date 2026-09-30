package com.twomemory.app.entry;

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
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestBody CreateEntryCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entryService.createDraft(actor.userId(), command));
    }

    @GetMapping("/{entryId}")
    public EntryView readEntry(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID entryId) {
        return entryService.readEntry(entryId, actor.userId());
    }

    @PostMapping("/{entryId}/publish")
    public PublishResult publish(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID entryId,
            @RequestBody PublishRequest request) {
        return entryService.publish(entryId, actor.userId(), request.baseVersion());
    }

    @PostMapping("/{entryId}/changes")
    public ApplyChangesResult applyChanges(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID entryId,
            @RequestBody ApplyChangesRequest request) {
        return entryService.applyChanges(entryId, actor.userId(), request.baseRevision(), request.mutations());
    }

    @PostMapping("/{entryId}/resolve")
    public EntryView resolveConflict(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID entryId,
            @RequestBody ResolveConflictCommand command) {
        return entryService.resolveConflict(entryId, actor.userId(), command);
    }

    @PostMapping("/{entryId}/comments")
    public CommentView addComment(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID entryId,
            @RequestBody CommentRequest request) {
        return commentService.addComment(entryId, actor.userId(), request.body(), request.replyToId());
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
