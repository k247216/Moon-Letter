package com.twomemory.app.couple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.auth.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoupleController.class)
@AutoConfigureMockMvc(addFilters = false)
class CoupleApiTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String TOKEN = "a".repeat(43);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CoupleService coupleService;

    @Test
    void ownerCreatesSpace() throws Exception {
        CoupleView view = view(SPACE, SpaceStatus.UNPAIRED, OWNER);
        when(coupleService.createSpace(OWNER)).thenReturn(new CreateSpaceResult(view, TOKEN, "INVITE"));

        mvc.perform(post("/api/v1/couple")
                        .with(TestAuth.deviceSession(OWNER, SPACE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couple.id").value(SPACE.toString()))
                .andExpect(jsonPath("$.pairingToken").value(TOKEN));
    }

    @Test
    void validPairingTokenBecomesUnusable() throws Exception {
        when(coupleService.pair(TOKEN, null)).thenReturn(new PairResult(
                view(SPACE, SpaceStatus.ACTIVE, OWNER, PARTNER), "partner-device-token", PARTNER));
        doThrow(new ConflictException("pairing token already used"))
                .when(coupleService).pair(TOKEN, null);

        mvc.perform(post("/api/v1/couple/pair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PairRequest(TOKEN, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void thirdUserIsRejected() throws Exception {
        doThrow(new ConflictException("couple space is full"))
                .when(coupleService).pair(TOKEN, null);

        mvc.perform(post("/api/v1/couple/pair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PairRequest(TOKEN, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void pairingTokenIsNotDisclosedOnSpaceRead() throws Exception {
        CoupleView view = view(SPACE, SpaceStatus.UNPAIRED, OWNER);
        when(coupleService.readSpace(OWNER, SPACE)).thenReturn(view);

        mvc.perform(get("/api/v1/couple/{coupleId}", SPACE)
                        .with(TestAuth.deviceSession(OWNER, SPACE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairingToken").doesNotExist())
                .andExpect(jsonPath("$.pairingCode").doesNotExist());
    }

    @Test
    void ownerCanReplaceAnOutstandingPairingToken() throws Exception {
        CoupleView view = view(SPACE, SpaceStatus.UNPAIRED, OWNER);
        when(coupleService.regeneratePairingToken(OWNER, SPACE))
                .thenReturn(new CreateSpaceResult(view, TOKEN, "INVITE"));

        mvc.perform(post("/api/v1/couple/{coupleId}/pairing-token", SPACE)
                        .with(TestAuth.deviceSession(OWNER, SPACE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairingToken").value(TOKEN));
    }

    @Test
    void nicknameTrimsWhitespaceAndUsesCodePointLimit() throws Exception {
        ProfileView profile = new ProfileView(OWNER, "小明", null, ThemeKind.PURE_WHITE);
        when(coupleService.updateProfile(eq(OWNER), eq(SPACE), eq(OWNER), any(UpdateProfileRequest.class)))
                .thenReturn(profile);

        mvc.perform(patch("/api/v1/couple/{coupleId}/members/{userId}/profile", SPACE, OWNER)
                        .with(TestAuth.deviceSession(OWNER, SPACE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest("  小明  ", null, "PURE_WHITE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("小明"));

        verify(coupleService).updateProfile(eq(OWNER), eq(SPACE), eq(OWNER), any(UpdateProfileRequest.class));
    }

    @Test
    void memberCannotUpdateOtherProfile() throws Exception {
        doThrow(new AccessDeniedException("member may only update own profile"))
                .when(coupleService).updateProfile(eq(OWNER), eq(SPACE), eq(PARTNER), any(UpdateProfileRequest.class));

        mvc.perform(patch("/api/v1/couple/{coupleId}/members/{userId}/profile", SPACE, PARTNER)
                        .with(TestAuth.deviceSession(OWNER, SPACE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest("小红", null, "WARM_BEIGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void themeOnlyAcceptsSupportedValues() throws Exception {
        doThrow(new ValidationException("unsupported theme"))
                .when(coupleService).updateProfile(eq(OWNER), eq(SPACE), eq(OWNER), any(UpdateProfileRequest.class));

        mvc.perform(patch("/api/v1/couple/{coupleId}/members/{userId}/profile", SPACE, OWNER)
                        .with(TestAuth.deviceSession(OWNER, SPACE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest("小明", null, "PINK"))))
                .andExpect(status().isBadRequest());
    }

    private static CoupleView view(UUID coupleId, SpaceStatus status, UUID... memberIds) {
        return new CoupleView(coupleId, status,
                List.of(memberIds).stream()
                        .map(id -> new MemberView(id, new ProfileView(id, "成员", null, ThemeKind.WARM_BEIGE)))
                        .toList());
    }
}
