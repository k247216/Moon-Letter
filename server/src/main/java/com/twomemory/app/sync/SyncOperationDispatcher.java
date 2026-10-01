package com.twomemory.app.sync;

import com.twomemory.app.entry.BlockMutation;
import com.twomemory.app.entry.BlockType;
import com.twomemory.app.entry.CreateEntryCommand;
import com.twomemory.app.entry.EntryMode;
import com.twomemory.app.entry.EntryService;
import com.twomemory.app.entry.EntryView;
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
        CREATE_PERSONAL_ENTRY;

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

    record DispatchOutcome(UUID entityId, String entityType, String operation, String responseBody) {
    }

    private final EntryService entryService;
    private final ObjectMapper objectMapper;

    public SyncOperationDispatcher(EntryService entryService, ObjectMapper objectMapper) {
        this.entryService = entryService;
        this.objectMapper = objectMapper;
    }

    public DispatchOutcome dispatch(UUID actorId, UUID coupleId, String operationType, String payload) {
        OperationType type = OperationType.from(operationType);
        return switch (type) {
            case CREATE_PERSONAL_ENTRY -> createPersonalEntry(actorId, coupleId, payload);
        };
    }

    private DispatchOutcome createPersonalEntry(UUID actorId, UUID coupleId, String payload) {
        if (payload == null || payload.isBlank()) {
            throw new SyncValidationException("operation payload is required");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new SyncValidationException("operation payload must be valid JSON");
        }
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
            throw new SyncValidationException("at least one text block is required");
        }
        List<BlockMutation> blocks = new ArrayList<>();
        for (JsonNode blockNode : root.path("blocks")) {
            if (!"TEXT".equalsIgnoreCase(blockNode.path("type").asText())) {
                throw new SyncValidationException("personal entry sync accepts TEXT blocks only");
            }
            long orderKey = blockNode.path("orderKey").asLong(-1);
            if (orderKey < 0) {
                throw new SyncValidationException("block orderKey must be a non-negative number");
            }
            blocks.add(new BlockMutation(
                    parseUuid(blockNode.path("blockId")),
                    BlockType.TEXT,
                    orderKey,
                    actorId,
                    objectMapper.createObjectNode().set("text",
                            objectMapper.valueToTree(blockNode.path("text").asText("")))
                            .toString(),
                    null,
                    false));
        }
        CreateEntryCommand command = new CreateEntryCommand(
                actorId, coupleId, EntryMode.PERSONAL, title, occurredAt, timezone, blocks,
                parseOptionalUuid(root.path("entryId")));
        EntryView entry = entryService.createDraft(actorId, command);
        String responseBody;
        try {
            responseBody = objectMapper.writeValueAsString(entry);
        } catch (Exception exception) {
            throw new IllegalStateException("could not serialize entry view", exception);
        }
        return new DispatchOutcome(entry.id(), "ENTRY", "CREATE", responseBody);
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
