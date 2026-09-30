package com.twomemory.app.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.auth.TestAuth;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EntryController.class)
@AutoConfigureMockMvc(addFilters = false)
class EntryApiTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ENTRY = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EntryService entryService;

    @MockBean
    private CommentService commentService;

    @Test
    void personalDraftIsPrivate() throws Exception {
        when(entryService.readEntry(eq(ENTRY), eq(USER))).thenThrow(new EntryAccessDeniedException("draft is private"));

        mvc.perform(get("/api/v1/entries/{entryId}", ENTRY)
                        .with(TestAuth.deviceSession(USER, SPACE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sameBlockConflictReturns409() throws Exception {
        doThrow(new EntryConflict(ENTRY, List.of(UUID.fromString("00000000-0000-0000-0000-000000000030"))))
                .when(entryService).applyChanges(eq(ENTRY), eq(1), any());

        ApplyChangesRequest request = new ApplyChangesRequest(1, List.of(
                new BlockMutation(UUID.fromString("00000000-0000-0000-0000-000000000030"),
                        BlockType.TEXT, 10, USER, "{\"text\":\"改\"}", null, false)));
        mvc.perform(post("/api/v1/entries/{entryId}/changes", ENTRY)
                        .with(TestAuth.deviceSession(USER, SPACE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void sharedEntryCanBeCreatedWithBothContributors() throws Exception {
        CreateEntryCommand command = new CreateEntryCommand(
                USER, SPACE, EntryMode.COLLABORATIVE, "周末", Instant.parse("2026-09-30T12:18:00Z"),
                "Asia/Shanghai", List.of());
        when(entryService.createDraft(any(CreateEntryCommand.class)))
                .thenReturn(new EntryView(ENTRY, SPACE, EntryMode.COLLABORATIVE, EntryState.DRAFT,
                        USER, 0, 0, List.of()));

        mvc.perform(post("/api/v1/entries")
                        .with(TestAuth.deviceSession(USER, SPACE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isCreated());
    }
}
