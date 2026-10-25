package com.twomemory.app.couple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.auth.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnniversaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnniversaryApiTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ANNIVERSARY = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnniversaryService anniversaryService;

    @Test
    void memberCanReadTheSharedAnniversary() throws Exception {
        when(anniversaryService.read(OWNER, SPACE)).thenReturn(view());

        mvc.perform(get("/api/v1/couple/{coupleId}/anniversary", SPACE)
                        .with(TestAuth.deviceSession(OWNER, SPACE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ANNIVERSARY.toString()))
                .andExpect(jsonPath("$.coupleId").value(SPACE.toString()))
                .andExpect(jsonPath("$.title").value("相遇日"))
                .andExpect(jsonPath("$.calendarType").value("GREGORIAN"))
                .andExpect(jsonPath("$.month").value(10))
                .andExpect(jsonPath("$.day").value(6))
                .andExpect(jsonPath("$.repeatsYearly").value(true));
    }

    @Test
    void missingAnniversaryIsReportedAsNotFound() throws Exception {
        when(anniversaryService.read(OWNER, SPACE)).thenReturn(null);

        mvc.perform(get("/api/v1/couple/{coupleId}/anniversary", SPACE)
                        .with(TestAuth.deviceSession(OWNER, SPACE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberCanSaveTheSharedAnniversary() throws Exception {
        SaveAnniversaryRequest request = new SaveAnniversaryRequest(
                "相遇日", "GREGORIAN", 10, 6, false, 2026, true);
        when(anniversaryService.save(eq(OWNER), eq(SPACE), any(SaveAnniversaryRequest.class)))
                .thenReturn(view());

        mvc.perform(put("/api/v1/couple/{coupleId}/anniversary", SPACE)
                        .with(TestAuth.deviceSession(OWNER, SPACE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("相遇日"));

        verify(anniversaryService).save(eq(OWNER), eq(SPACE), any(SaveAnniversaryRequest.class));
    }

    @Test
    void nonMemberCannotSaveTheSharedAnniversary() throws Exception {
        doThrow(new AccessDeniedException("user is not a member of this space"))
                .when(anniversaryService).save(eq(OWNER), eq(SPACE), any(SaveAnniversaryRequest.class));

        mvc.perform(put("/api/v1/couple/{coupleId}/anniversary", SPACE)
                        .with(TestAuth.deviceSession(OWNER, SPACE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"相遇日\",\"calendarType\":\"GREGORIAN\",\"month\":10,\"day\":6}"))
                .andExpect(status().isForbidden());
    }

    private static AnniversaryView view() {
        return new AnniversaryView(ANNIVERSARY, SPACE, "相遇日", CalendarType.GREGORIAN,
                10, 6, false, 2026, true);
    }
}
