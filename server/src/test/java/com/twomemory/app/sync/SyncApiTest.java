package com.twomemory.app.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.auth.SpaceAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
@AutoConfigureMockMvc(addFilters = false)
class SyncApiTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OPERATION = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final String BODY_TRUE = "{\"saved\":true}";
    private static final String BODY_FALSE = "{\"saved\":false}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IdempotencyService idempotencyService;

    @MockBean
    private ChangeFeedService changeFeedService;

    @MockBean
    private SyncNotificationPublisher notificationPublisher;

    @MockBean
    private SpaceAccessPolicy accessPolicy;

    @Test
    void duplicateOperationReturnsOriginalResult() throws Exception {
        SyncOperationRequest request = new SyncOperationRequest(
                OPERATION, SPACE, "", "ENTRY", OPERATION, "UPSERT", BODY_TRUE);
        String hash = SyncPayloadHasher.hash(request);
        request = new SyncOperationRequest(OPERATION, SPACE, hash, "ENTRY", OPERATION, "UPSERT", BODY_TRUE);
        when(idempotencyService.executeOnce(eq(OPERATION), eq(USER), eq(hash), any()))
                .thenReturn(new MutationResult(200, BODY_TRUE, true));

        mvc.perform(post("/api/v1/sync/operations")
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));
    }

    @Test
    void reusedOperationIdWithDifferentPayloadIsRejected() throws Exception {
        SyncOperationRequest request = new SyncOperationRequest(
                OPERATION, SPACE, "", "ENTRY", OPERATION, "UPSERT", BODY_FALSE);
        String hash = SyncPayloadHasher.hash(request);
        request = new SyncOperationRequest(OPERATION, SPACE, hash, "ENTRY", OPERATION, "UPSERT", BODY_FALSE);
        when(idempotencyService.executeOnce(eq(OPERATION), eq(USER), eq(hash), any()))
                .thenThrow(new SyncConflictException("operation id was used with another payload"));

        mvc.perform(post("/api/v1/sync/operations")
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void pageLimitAboveTwoHundredIsRejected() throws Exception {
        when(changeFeedService.readChanges(eq(USER), eq(SPACE), eq(0L), eq(201)))
                .thenThrow(new SyncValidationException("limit must be between 1 and 200"));

        mvc.perform(get("/api/v1/sync/changes")
                        .header("X-User-Id", USER)
                        .param("coupleId", SPACE.toString())
                        .param("after", "0")
                        .param("limit", "201"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tombstonesAndMonotonicSequenceAreReturned() throws Exception {
        when(changeFeedService.readChanges(eq(USER), eq(SPACE), eq(8L), eq(50)))
                .thenReturn(new ChangePage(List.of(
                        new ChangeView(9L, "ENTRY", OPERATION, "DELETE", null, Instant.parse("2026-09-30T12:18:00Z"))),
                        9L, false));

        mvc.perform(get("/api/v1/sync/changes")
                        .header("X-User-Id", USER)
                        .param("coupleId", SPACE.toString())
                        .param("after", "8")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextSequence").value(9))
                .andExpect(jsonPath("$.changes[0].payload").doesNotExist());
    }

    @Test
    void anotherSpaceFeedIsForbidden() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "space access denied"))
                .when(changeFeedService).readChanges(eq(USER), eq(SPACE), eq(0L), eq(50));

        mvc.perform(get("/api/v1/sync/changes")
                        .header("X-User-Id", USER)
                        .param("coupleId", SPACE.toString())
                        .param("after", "0")
                        .param("limit", "50"))
                .andExpect(status().isForbidden());
    }
}
