package com.twomemory.app.capsule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.auth.TestAuth;
import com.twomemory.app.auth.SpaceAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL and HTTP contracts; each fixture rolls back. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CapsuleApiTest {
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabaseConfig::applicationUrl);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired SpaceAccessPolicy access;
    UUID space, otherSpace, author, partner, outsider;
    String root;
    LocalDate today;

    @BeforeEach void setup() {
        TestSecurityContextHolder.clearContext();
        space = UUID.randomUUID(); otherSpace = UUID.randomUUID();
        author = UUID.randomUUID(); partner = UUID.randomUUID(); outsider = UUID.randomUUID();
        for (UUID id : new UUID[]{space, otherSpace})
            db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", id);
        for (UUID id : new UUID[]{author, partner, outsider})
            db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", id);
        for (UUID id : new UUID[]{author, partner})
            db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", space, id);
        db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", otherSpace, outsider);
        root = "/api/v1/couple/" + space + "/capsules";
        today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    }
    Map<String,Object> letter(String visibility, LocalDate date) {
        return new HashMap<>(Map.of("title", "Future letter", "body", "secret letter body",
                "visibility", visibility, "unlockDate", date.toString()));
    }
    ResultActions save(UUID actor, UUID id, Map<String,Object> body) throws Exception {
        return mvc.perform(put(root + "/" + id).with(TestAuth.deviceSession(actor, space))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    ResultActions list(UUID actor) throws Exception {
        return mvc.perform(get(root).with(TestAuth.deviceSession(actor, space)));
    }
    ResultActions remove(UUID actor, UUID id) throws Exception {
        return mvc.perform(delete(root + "/" + id).with(TestAuth.deviceSession(actor, space)));
    }

    @Test void sealedEnvelopeRedactsPartnerBodyButAuthorCanRestoreIt() throws Exception {
        UUID id = UUID.randomUUID();
        var input = letter("SHARED", today.plusDays(1));
        var response = save(author, id, input).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.coupleId").value(space.toString()))
                .andExpect(jsonPath("$.authorId").value(author.toString()))
                .andExpect(jsonPath("$.visibility").value("SHARED"))
                .andExpect(jsonPath("$.unlockDate").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.body").value("secret letter body"))
                .andExpect(jsonPath("$.unlocked").value(false))
                .andExpect(jsonPath("$.updatedAt").isString()).andReturn();
        assertThat(json.readTree(response.getResponse().getContentAsString()).size()).isEqualTo(9);
        var retry = save(author, id, input).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(retry.getResponse().getContentAsString()).get("updatedAt"))
                .isEqualTo(json.readTree(response.getResponse().getContentAsString()).get("updatedAt"));
        list(partner).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Future letter"))
                .andExpect(jsonPath("$[0].body").isEmpty()).andExpect(jsonPath("$[0].unlocked").value(false));
        list(author).andExpect(jsonPath("$[0].body").value("secret letter body"));
        assertThat(db.queryForObject("SELECT count(*) FROM capsule_letter WHERE couple_id=?", Integer.class, space)).isEqualTo(1);
    }
    @Test void exactDueDateRevealsSharedButPrivateNeverAppearsToPartner() throws Exception {
        save(author, UUID.randomUUID(), letter("SHARED", today)).andExpect(status().isOk());
        save(author, UUID.randomUUID(), letter("PRIVATE", today.minusDays(1))).andExpect(status().isOk());
        list(partner).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("secret letter body"))
                .andExpect(jsonPath("$[0].unlocked").value(true));
        list(author).andExpect(jsonPath("$.length()").value(2));
    }
    @Test void onlyOriginalAuthorMayEditVisibilityOrDelete() throws Exception {
        UUID id = UUID.randomUUID();
        save(author, id, letter("SHARED", today)).andExpect(status().isOk());
        save(partner, id, letter("PRIVATE", today)).andExpect(status().isForbidden());
        remove(partner, id).andExpect(status().isForbidden());
        save(author, id, letter("PRIVATE", today)).andExpect(status().isOk());
        list(partner).andExpect(jsonPath("$.length()").value(0));
        save(partner, id, letter("SHARED", today)).andExpect(status().isForbidden());
        remove(partner, id).andExpect(status().isForbidden());
        remove(author, id).andExpect(status().isNoContent());
        remove(author, id).andExpect(status().isNoContent());
        list(author).andExpect(jsonPath("$.length()").value(0));
        save(author, id, letter("SHARED", today)).andExpect(status().isGone());
        remove(author, UUID.randomUUID()).andExpect(status().isNotFound());
    }
    @Test void outsidersFormerAndDeletedMembersCannotReadWriteOrDelete() throws Exception {
        UUID id = UUID.randomUUID();
        save(author, id, letter("SHARED", today)).andExpect(status().isOk());
        for (String column : new String[]{"left_at", "deleted_at"}) {
            db.update("UPDATE couple_member SET " + column + "=now() WHERE couple_id=? AND user_id=?", space, partner);
            for (UUID actor : new UUID[]{partner, outsider}) {
                list(actor).andExpect(status().isForbidden());
                save(actor, id, letter("SHARED", today)).andExpect(status().isForbidden());
                remove(actor, id).andExpect(status().isForbidden());
            }
            db.update("UPDATE couple_member SET " + column + "=NULL WHERE couple_id=? AND user_id=?", space, partner);
        }
    }
    @Test void inactiveAndUnauthenticatedUsersCannotAccessAnyEndpoint() throws Exception {
        UUID id = UUID.randomUUID();
        db.update("UPDATE app_user SET status='DISABLED' WHERE id=?", author);
        list(author).andExpect(status().isUnauthorized());
        save(author, id, letter("SHARED", today)).andExpect(status().isUnauthorized());
        remove(author, id).andExpect(status().isUnauthorized());
        TestSecurityContextHolder.clearContext();
        mvc.perform(get(root)).andExpect(status().isUnauthorized());
        mvc.perform(put(root + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(letter("SHARED", today)))).andExpect(status().isUnauthorized());
        mvc.perform(delete(root + "/" + id)).andExpect(status().isUnauthorized());
    }
    @Test void crossSpaceIdsCannotBeReusedEditedOrDeletedEvenByAuthorMemberOfBoth() throws Exception {
        UUID id = UUID.randomUUID();
        save(author, id, letter("SHARED", today)).andExpect(status().isOk());
        db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", otherSpace, author);
        root = "/api/v1/couple/" + otherSpace + "/capsules";
        list(author).andExpect(jsonPath("$.length()").value(0));
        save(author, id, letter("SHARED", today)).andExpect(status().isForbidden());
        remove(author, id).andExpect(status().isForbidden());
        root = "/api/v1/couple/" + space + "/capsules";
        list(author).andExpect(jsonPath("$[0].body").value("secret letter body"));
        remove(author, id).andExpect(status().isNoContent());
        root = "/api/v1/couple/" + otherSpace + "/capsules";
        save(author, id, letter("SHARED", today)).andExpect(status().isForbidden());
    }
    @Test void validatesTextBoundsVisibilityAndStrictPositiveGregorianDates() throws Exception {
        for (String field : new String[]{"title", "body", "unlockDate", "visibility"}) {
            var input = letter("SHARED", today); input.remove(field);
            save(author, UUID.randomUUID(), input).andExpect(status().isBadRequest());
        }
        for (String title : new String[]{"", "   ", "x".repeat(81)}) {
            var input = letter("SHARED", today); input.put("title", title);
            save(author, UUID.randomUUID(), input).andExpect(status().isBadRequest());
        }
        for (String body : new String[]{"", "x".repeat(20001)}) {
            var input = letter("SHARED", today); input.put("body", body);
            save(author, UUID.randomUUID(), input).andExpect(status().isBadRequest());
        }
        for (String date : new String[]{"0000-01-01", "-0001-01-01", "2027-02-29", "2026-02-30", "2026-1-01", "2026-01-01T00:00:00Z", "+10000-01-01"}) {
            var input = letter("SHARED", today); input.put("unlockDate", date);
            save(author, UUID.randomUUID(), input).andExpect(status().isBadRequest());
        }
        save(author, UUID.randomUUID(), letter("PUBLIC", today)).andExpect(status().isBadRequest());
        var valid = letter("PRIVATE", LocalDate.of(2028, 2, 29));
        valid.put("title", "😀".repeat(80)); valid.put("body", "😀".repeat(20000));
        save(author, UUID.randomUUID(), valid).andExpect(status().isOk());
        valid.put("title", "x"); valid.put("body", "x"); valid.put("unlockDate", "0001-01-01");
        save(author, UUID.randomUUID(), valid).andExpect(status().isOk());
    }

    @Test void serverShanghaiMidnightControlsRevealRegardlessOfClockZoneOrClientDate() throws Exception {
        UUID id = UUID.randomUUID();
        var request = letter("SHARED", LocalDate.of(2030, 1, 2));
        request.put("unlocked", true); request.put("serverDate", "2099-01-01");
        request.put("authorId", partner.toString());
        save(author, id, request).andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(author.toString()));
        var before = new CapsuleService(db, access, Clock.fixed(Instant.parse("2030-01-01T15:59:59.999999999Z"), ZoneId.of("Pacific/Honolulu")));
        assertThat(before.list(partner, space)).singleElement().satisfies(view -> {
            assertThat(view.body()).isNull(); assertThat(view.unlocked()).isFalse();
        });
        assertThat(before.list(author, space)).singleElement().satisfies(view -> {
            assertThat(view.body()).isEqualTo("secret letter body"); assertThat(view.unlocked()).isFalse();
        });
        var midnight = new CapsuleService(db, access, Clock.fixed(Instant.parse("2030-01-01T16:00:00Z"), ZoneId.of("UTC")));
        assertThat(midnight.list(partner, space)).singleElement().satisfies(view -> {
            assertThat(view.body()).isEqualTo("secret letter body"); assertThat(view.unlocked()).isTrue();
        });
        save(author, id, letter("PRIVATE", LocalDate.of(2030, 1, 2))).andExpect(status().isOk());
        assertThat(midnight.list(partner, space)).isEmpty();
        assertThat(midnight.list(author, space)).singleElement().satisfies(view -> assertThat(view.body()).isEqualTo("secret letter body"));
    }

    @Test void independentSnapshotWritesDoNotPutLetterBodiesInLegacyEntriesFeedOrNotifications() throws Exception {
        UUID id = UUID.randomUUID();
        int legacyCount = db.queryForObject("SELECT count(*) FROM time_capsule", Integer.class);
        save(author, id, letter("SHARED", today.plusDays(1))).andExpect(status().isOk());
        save(author, UUID.randomUUID(), letter("PRIVATE", today.minusDays(1))).andExpect(status().isOk());
        save(author, id, letter("PRIVATE", today.plusDays(1))).andExpect(status().isOk());
        remove(author, id).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT count(*) FROM time_capsule", Integer.class)).isEqualTo(legacyCount);
        assertThat(db.queryForObject("SELECT count(*) FROM entry WHERE couple_id=?", Integer.class, space)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM sync_change WHERE couple_id=?", Integer.class, space)).isZero();
        var feed = mvc.perform(get("/api/v1/sync/changes").param("coupleId", space.toString())
                .with(TestAuth.deviceSession(partner, space))).andExpect(status().isOk()).andReturn();
        assertThat(feed.getResponse().getContentAsString()).doesNotContain("secret letter body");
        var export = mvc.perform(get("/api/v1/export").param("coupleId", space.toString())
                .with(TestAuth.deviceSession(partner, space))).andExpect(status().isOk()).andReturn();
        assertThat(export.getResponse().getContentAsString()).doesNotContain("secret letter body");
    }
}
