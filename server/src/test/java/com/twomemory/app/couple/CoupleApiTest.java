package com.twomemory.app.couple;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CoupleService coupleService;

    @Test
    void ownerCreatesSpace() throws Exception {
        CoupleView view = view(SPACE, SpaceStatus.UNPAIRED, OWNER);
        when(coupleService.createSpace(OWNER)).thenReturn(new CreateSpaceResult(view, "123456"));

        mvc.perform(post("/api/v1/couple")
                        .header("X-User-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couple.id").value(SPACE.toString()))
                .andExpect(jsonPath("$.pairingCode").value("123456"));
    }

    @Test
    void validPairingCodeBecomesUnusable() throws Exception {
        CoupleView view = view(SPACE, SpaceStatus.ACTIVE, OWNER, PARTNER);
        when(coupleService.pair(PARTNER, "123456")).thenReturn(new PairResult(view));
        doThrow(new ConflictException("pairing code already used"))
                .when(coupleService).pair(PARTNER, "123456");

        mvc.perform(post("/api/v1/couple/pair")
                        .header("X-User-Id", PARTNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PairRequest("123456"))))
                .andExpect(status().isConflict());
    }

    @Test
    void thirdUserIsRejected() throws Exception {
        doThrow(new ConflictException("couple space is full"))
                .when(coupleService).pair(PARTNER, "123456");

        mvc.perform(post("/api/v1/couple/pair")
                        .header("X-User-Id", PARTNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PairRequest("123456"))))
                .andExpect(status().isConflict());
    }

    @Test
    void nicknameTrimsWhitespaceAndUsesCodePointLimit() throws Exception {
        ProfileView profile = new ProfileView(OWNER, "小明", null, ThemeKind.PURE_WHITE);
        when(coupleService.updateProfile(eq(OWNER), eq(SPACE), eq(OWNER), any(UpdateProfileRequest.class)))
                .thenReturn(profile);

        mvc.perform(patch("/api/v1/couple/{coupleId}/members/{userId}/profile", SPACE, OWNER)
                        .header("X-User-Id", OWNER)
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
                        .header("X-User-Id", OWNER)
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
                        .header("X-User-Id", OWNER)
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
