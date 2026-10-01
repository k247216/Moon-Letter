package com.twomemory.app.sync;

import com.twomemory.app.entry.BlockMutation;
import com.twomemory.app.entry.BlockType;
import com.twomemory.app.entry.CommentService;
import com.twomemory.app.entry.CommentView;
import com.twomemory.app.entry.CreateEntryCommand;
import com.twomemory.app.entry.EntryMode;
import com.twomemory.app.entry.EntryService;
import com.twomemory.app.entry.EntryView;
import com.twomemory.app.entry.PublishResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Closed dispatcher for typed sync operations. Clients can no longer push
 * arbitrary change payloads: every operation type is enumerated here and its
 * payload is validated before the business mutation runs.
 */
@Service
public class SyncOperationDispatcher {

    enum OperationType {
        CREATE_PERSONAL_ENTRY,
        CREATE_SHARED_ENTRY,
        APPEND_BLOCK,
        ADD_COMMENT,
        PUBLISH_ENTRY;

        static OperationType from(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new SyncValidationException("operation type is required");
            }
            try {
                return OperationType.valueOf(raw.trim());
            } catch (IllegalArgumentException exception) {
                throw new SyncValidationException("unknown operation type: " + raw);
            }
        }
    }

    /** Who writes the change row for an operation. */
    enum ChangeRowOwnership {
        /**
         * The service executing the operation already appended its own change
         * row, so the controller must not append another one. Double-appending
         * turns one mutation into two feed rows and makes every replay, even
         * under a fresh operation id, grow the feed for a change that did not happen.
         */
        APPENDED_BY_MUTATION,
        /** The controller appends the row from this outcome's payload. */
        APPENDED_BY_CONTROLLER,
        /** Nothing in this operation may reach the other device: no row, no wake-up. */
        SUPPRESSED
    }

    record DispatchOutcome(UUID entityId, String entityType, String operation, String responseBody,
                           ChangeRowOwnership changeRow) {
    }

    private final EntryService entryService;
    private final CommentService commentService;
    private final ObjectMapper objectMapper;

    public SyncOperationDispatcher(EntryService entryService, CommentService commentService,
                                   ObjectMapper objectMapper) {
        this.entryService = entryService;
        this.commentService = commentService;
        this.objectMapper = objectMapper;
    }

    public DispatchOutcome dispatch(UUID actorId, UUID coupleId, String operationType, String payload) {
        OperationType type = OperationType.from(operationType);
        return switch (type) {
            case CREATE_PERSONAL_ENTRY -> createEntry(actorId, coupleId, payload, EntryMode.PERSONAL);
            case CREATE_SHARED_ENTRY -> createEntry(actorId, coupleId, payload, EntryMode.COLLABORATIVE);
            case APPEND_BLOCK -> appendBlock(actorId, payload);
            case ADD_COMMENT -> addComment(actorId, payload);
            case PUBLISH_ENTRY -> publishEntry(actorId, payload);
        };
    }

    /**
     * PUBLISH_ENTRY moves the author's own draft to PUBLISHED. The client sends the
     * rowVersion its local copy was built from, so a stale base is rejected as a
     * conflict instead of silently overwriting a newer revision. EntryService.publish
     * owns the PUBLISH change row and writes the whole entry into it: for a personal
     * record this row is the partner device's first sight of the entry, and a payload
     * with just an id would leave it with nothing to rebuild.
     */
    private DispatchOutcome publishEntry(UUID actorId, String payload) {
        JsonNode root = parsePayload(payload);
        UUID entryId = parseUuid(root.path("entryId"));
        JsonNode baseVersion = root.path("baseVersion");
        if (!baseVersion.canConvertToLong()) {
            throw new SyncValidationException("baseVersion is required to publish");
        }
        PublishResult result = entryService.publish(entryId, actorId, baseVersion.asLong());
        try {
            return new DispatchOutcome(entryId, "ENTRY", "PUBLISH",
                    objectMapper.writeValueAsString(result.entry()), ChangeRowOwnership.APPENDED_BY_MUTATION);
        } catch (Exception exception) {
            throw new IllegalStateException("could not serialize published entry", exception);
        }
    }

    private DispatchOutcome createEntry(UUID actorId, UUID coupleId, String payload, EntryMode mode) {
        if (payload == null || payload.isBlank()) {
            throw new SyncValidationException("operation payload is required");
        }
        JsonNode root = parsePayload(payload);
        if (!actorId.equals(parseUuid(root.path("authorId")))) {
            throw new SyncValidationException("entry author must be the authenticated user");
        }
        String title = root.path("title").asText(null);
        Instant occurredAt = parseInstant(root.path("occurredAt"));
        String timezone = root.path("occurredTimezone").asText(null);
        if (timezone == null || timezone.isBlank()) {
            throw new SyncValidationException("occurredTimezone is required");
        }
        if (!root.path("blocks").isArray() || root.path("blocks").isEmpty()) {
            throw new SyncValidationException("at least one block is required");
        }
        List<BlockMutation> blocks = new ArrayList<>();
        for (JsonNode blockNode : root.path("blocks")) {
            String rawType = blockNode.path("type").asText("TEXT");
            if (!"TEXT".equalsIgnoreCase(rawType) && !"IMAGE".equalsIgnoreCase(rawType)) {
                throw new SyncValidationException("entry sync accepts TEXT or IMAGE blocks only");
            }
            long orderKey = blockNode.path("orderKey").asLong(-1);
            if (orderKey < 0) {
                throw new SyncValidationException("block orderKey must be a non-negative number");
            }
            String payloadJson = blockTextPayload(blockNode, rawType);
            blocks.add(new BlockMutation(
                    parseUuid(blockNode.path("blockId")),
                    BlockType.valueOf(rawType.toUpperCase()),
                    orderKey,
                    actorId,
                    payloadJson,
                    parseOptionalUuid(blockNode.path("assetId")),
                    false));
        }
        CreateEntryCommand command = new CreateEntryCommand(
                actorId, coupleId, mode, title, occurredAt, timezone, blocks,
                parseOptionalUuid(root.path("entryId")));
        EntryView entry = entryService.createDraft(actorId, command);
        return entryOutcome(entry, "CREATE");
    }

    /**
     * APPEND_BLOCK adds the caller's own block to an existing entry (the
     * shared-perspective write). Appends of new block ids cannot conflict
     * with concurrent edits, so baseRevision 0 is safe here.
     */
    private DispatchOutcome appendBlock(UUID actorId, String payload) {
        JsonNode root = parsePayload(payload);
        UUID entryId = parseUuid(root.path("entryId"));
        JsonNode blockNode = root.path("block");
        if (!blockNode.isObject()) {
            throw new SyncValidationException("block object is required");
        }
        String rawType = blockNode.path("type").asText("TEXT");
        if (!"TEXT".equalsIgnoreCase(rawType) && !"IMAGE".equalsIgnoreCase(rawType)) {
            throw new SyncValidationException("block sync accepts TEXT or IMAGE only");
        }
        long orderKey = blockNode.path("orderKey").asLong(-1);
        if (orderKey < 0) {
            throw new SyncValidationException("block orderKey must be a non-negative number");
        }
        BlockMutation mutation = new BlockMutation(
                parseUuid(blockNode.path("blockId")),
                BlockType.valueOf(rawType.toUpperCase()),
                orderKey,
                actorId,
                blockTextPayload(blockNode, rawType),
                parseOptionalUuid(blockNode.path("assetId")),
                false);
        EntryView updated = entryService
                .applyChanges(entryId, actorId, 0, List.of(mutation))
                .entry();
        return entryOutcome(updated, "UPDATE");
    }

    private DispatchOutcome addComment(UUID actorId, String payload) {
        JsonNode root = parsePayload(payload);
        UUID entryId = parseUuid(root.path("entryId"));
        UUID commentId = parseUuid(root.path("commentId"));
        String body = root.path("body").asText(null);
        UUID replyToId = parseOptionalUuid(root.path("replyToId"));
        CommentView view = commentService.addComment(entryId, actorId, commentId, body, replyToId);
        try {
            return new DispatchOutcome(entryId, "COMMENT", "ADD",
                    objectMapper.writeValueAsString(view), ChangeRowOwnership.APPENDED_BY_MUTATION);
        } catch (Exception exception) {
            throw new IllegalStateException("could not serialize comment view", exception);
        }
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new SyncValidationException("operation payload must be valid JSON");
        }
    }

    private String blockTextPayload(JsonNode blockNode, String rawType) {
        if ("IMAGE".equalsIgnoreCase(rawType)) {
            // IMAGE payload keeps whatever the client stored (local uri, caption).
            return blockNode.path("payload").isTextual() ? blockNode.path("payload").asText()
                    : blockNode.path("payload").toString();
        }
        return objectMapper.createObjectNode().set("text",
                objectMapper.valueToTree(blockNode.path("text").asText(""))).toString();
    }

    /**
     * Entry mutations broadcast the whole projection, so a pulling device can
     * rebuild the record from the row alone. A personal draft is the exception:
     * it has no audience but its author, and the couple feed is the channel that
     * reaches the other device, so no row is written for it at all.
     */
    private DispatchOutcome entryOutcome(EntryView entry, String operation) {
        try {
            return new DispatchOutcome(entry.id(), "ENTRY", operation,
                    objectMapper.writeValueAsString(entry),
                    entry.privateDraft()
                            ? ChangeRowOwnership.SUPPRESSED
                            : ChangeRowOwnership.APPENDED_BY_CONTROLLER);
        } catch (Exception exception) {
            throw new IllegalStateException("could not serialize entry view", exception);
        }
    }

    private static UUID parseUuid(JsonNode node) {
        String raw = node.asText(null);
        if (raw == null || raw.isBlank()) {
            throw new SyncValidationException("uuid field is required");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw new SyncValidationException("uuid field is malformed: " + raw);
        }
    }

    private static UUID parseOptionalUuid(JsonNode node) {
        String raw = node.asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseUuid(node);
    }

    private static Instant parseInstant(JsonNode node) {
        String raw = node.asText(null);
        if (raw == null || raw.isBlank()) {
            throw new SyncValidationException("occurredAt is required");
        }
        try {
            return Instant.parse(raw);
        } catch (Exception exception) {
            throw new SyncValidationException("occurredAt must be an ISO-8601 instant");
        }
    }
}
