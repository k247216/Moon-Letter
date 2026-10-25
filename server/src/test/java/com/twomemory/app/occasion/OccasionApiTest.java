package com.twomemory.app.occasion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.auth.TestAuth;
import com.twomemory.app.media.MediaService;
import com.twomemory.app.media.LocalObjectStorage;
import com.twomemory.app.media.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL, real services, authenticated HTTP principals; fixtures roll back. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class OccasionApiTest {
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabaseConfig::applicationUrl);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired MediaService media;
    @Autowired ObjectStorage storage;
    UUID space, otherSpace, author, partner, outsider;
    String root;

    @BeforeEach void setup() {
        space = UUID.randomUUID(); otherSpace = UUID.randomUUID();
        author = UUID.randomUUID(); partner = UUID.randomUUID(); outsider = UUID.randomUUID();
        for (UUID id : new UUID[]{space, otherSpace})
            db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", id);
        for (UUID id : new UUID[]{author, partner, outsider})
            db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", id);
        for (UUID id : new UUID[]{author, partner})
            db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", space, id);
        db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", otherSpace, outsider);
        root = "/api/v1/couple/" + space + "/occasions";
    }

    Map<String,Object> card(String kind, String date) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("kind", kind); body.put("title", "Our day"); body.put("body", "A private message");
        body.put("date", date); body.put("repeatsYearly", true);
        if (kind.equals("SURPRISE")) body.put("recipientId", partner);
        return body;
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
    UUID asset(UUID owner, UUID couple, String kind, String status) {
        UUID id = UUID.randomUUID();
        db.update("""
                INSERT INTO media_asset(id,couple_id,owner_id,kind,status,object_key,mime_type,byte_size,sha256)
                VALUES (?,?,?,?::media_kind,?::media_status,?,'image/png',10,?)
                """, id, couple, owner, kind, status, "occasion-test/" + id, "a".repeat(64));
        return id;
    }

    @Test void privateSurpriseCanBePreparedBeforePartnerJoinsAndIsNeverAutomaticallyShared() throws Exception {
        db.update("UPDATE couple_member SET left_at=now() WHERE couple_id=? AND user_id=?", space,partner);
        UUID id=UUID.randomUUID(), photo=asset(author,space,"IMAGE","READY");
        var body=card("SURPRISE","2020-01-01"); body.remove("recipientId");
        body.put("visibility","PRIVATE"); body.put("photoAssetId",photo);
        save(author,id,body).andExpect(status().isOk()).andExpect(jsonPath("$.recipientId").isEmpty());
        db.update("UPDATE couple_member SET left_at=NULL WHERE couple_id=? AND user_id=?",space,partner);
        list(partner).andExpect(jsonPath("$.length()").value(0));
        assertThatThrownBy(() -> media.readAsset(partner,photo)).hasMessage("media is not shared");
        body.remove("visibility");
        save(author,id,body).andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"));
        body.put("visibility","SHARED");
        save(author,id,body).andExpect(status().isBadRequest());
        body.put("recipientId",partner); body.put("date",LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString());
        save(author,id,body).andExpect(status().isOk());
        list(partner).andExpect(jsonPath("$.length()").value(0));
        body.put("date","2020-01-01"); save(author,id,body).andExpect(status().isOk());
        list(partner).andExpect(jsonPath("$.length()").value(1));
        assertThatCode(() -> media.readAsset(partner,photo)).doesNotThrowAnyException();
    }

    @Test void privateOccasionsAreAuthorOnlyEvenAfterSurpriseRevealAndLegacyEdits() throws Exception {
        UUID photo = asset(author, space, "IMAGE", "READY");
        for (String kind : new String[]{"BIRTHDAY", "ANNIVERSARY", "SURPRISE"}) {
            UUID id = UUID.randomUUID();
            var body = card(kind, "2020-01-01"); body.put("visibility", "PRIVATE"); body.put("photoAssetId", photo);
            save(author, id, body).andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"));
            list(partner).andExpect(jsonPath("$.length()").value(0));
            mvc.perform(get(root + "/" + id).with(TestAuth.deviceSession(partner, space))).andExpect(status().isNotFound());
            mvc.perform(get(root + "/" + id).with(TestAuth.deviceSession(author, space)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"));
            assertThatThrownBy(() -> media.readAsset(partner, photo)).hasMessage("media is not shared");
            var legacy = card(kind, "2020-01-02"); legacy.put("photoAssetId", photo);
            save(partner, id, legacy).andExpect(status().isForbidden());
            remove(partner, id).andExpect(status().isForbidden());
            save(author, id, legacy).andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"));
            list(author).andExpect(jsonPath("$.length()").value(1));
            remove(author, id).andExpect(status().isNoContent());
        }
    }

    @Test void sharedOccasionVisibilityChangesOnlyByAuthorAndUnhideKeepsSurpriseSchedule() throws Exception {
        UUID id=UUID.randomUUID(); var body=card("BIRTHDAY", "2020-01-01");
        save(author,id,body).andExpect(jsonPath("$.visibility").value("SHARED"));
        body.put("visibility","PRIVATE");
        save(partner,id,body).andExpect(status().isForbidden());
        save(author,id,body).andExpect(status().isOk());
        list(partner).andExpect(jsonPath("$.length()").value(0));
        body.put("visibility","SHARED");
        save(author,id,body).andExpect(status().isOk());
        list(partner).andExpect(jsonPath("$.length()").value(1));
        body.put("visibility","UNKNOWN");
        save(author,id,body).andExpect(status().isBadRequest());
        var legacy=new SaveOccasionRequest("BIRTHDAY","title","body","2020-01-01",true,null,null);
        assertThat(legacy.visibility()).isNull();
        var view=new OccasionView(id,space,author,OccasionKind.BIRTHDAY,"title","body",LocalDate.of(2020,1,1),true,null,null,java.time.Instant.now());
        assertThat(view.visibility()).isEqualTo("SHARED");
    }

    @Test void multipleDatesAndUuidRetryKeepAllCardsAndContract() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Map<String,Object> birthday = card("BIRTHDAY", "2028-02-29");
        for (int retry = 0; retry < 2; retry++)
            save(author, first, birthday).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(first.toString()))
                    .andExpect(jsonPath("$.coupleId").value(space.toString()))
                    .andExpect(jsonPath("$.authorId").value(author.toString()))
                    .andExpect(jsonPath("$.kind").value("BIRTHDAY"))
                    .andExpect(jsonPath("$.date").value("2028-02-29"))
                    .andExpect(jsonPath("$.repeatsYearly").value(true))
                    .andExpect(jsonPath("$.recipientId").isEmpty())
                    .andExpect(jsonPath("$.photoAssetId").isEmpty())
                    .andExpect(jsonPath("$.updatedAt").isString());
        save(author, second, card("ANNIVERSARY", "2027-10-06")).andExpect(status().isOk());
        list(partner).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertThat(db.queryForObject("SELECT count(*) FROM occasion WHERE couple_id=?", Integer.class, space)).isEqualTo(2);
    }

    @Test void sharedCardsCanBeEditedAndDeletedByPartnerWithoutChangingOriginalAuthor() throws Exception {
        for (String kind : new String[]{"BIRTHDAY", "ANNIVERSARY"}) {
            UUID id = UUID.randomUUID();
            save(author, id, card(kind, "2026-10-04")).andExpect(status().isOk());
            var edit = card(kind, "2026-10-05"); edit.put("title", "Changed together");
            save(partner, id, edit).andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorId").value(author.toString()))
                    .andExpect(jsonPath("$.title").value("Changed together"));
            remove(partner, id).andExpect(status().isNoContent());
        }
        list(author).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void outsidersAndFormerMembersCannotListWriteOrDelete() throws Exception {
        UUID id = UUID.randomUUID();
        save(author, id, card("BIRTHDAY", "2026-10-04")).andExpect(status().isOk());
        db.update("UPDATE couple_member SET left_at=now() WHERE couple_id=? AND user_id=?", space, partner);
        for (UUID actor : new UUID[]{outsider, partner}) {
            list(actor).andExpect(status().isForbidden());
            save(actor, id, card("BIRTHDAY", "2026-10-05")).andExpect(status().isForbidden());
            remove(actor, id).andExpect(status().isForbidden());
        }
    }

    @Test void uuidCannotHijackAnotherSpaceEvenIfActorBelongsToBoth() throws Exception {
        UUID id = UUID.randomUUID();
        save(author, id, card("ANNIVERSARY", "2026-10-04")).andExpect(status().isOk());
        db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", otherSpace, author);
        String path = "/api/v1/couple/" + otherSpace + "/occasions/" + id;
        mvc.perform(put(path).with(TestAuth.deviceSession(author, otherSpace)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(card("BIRTHDAY", "2026-10-05")))).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(TestAuth.deviceSession(author, otherSpace))).andExpect(status().isForbidden());
        list(partner).andExpect(jsonPath("$[0].kind").value("ANNIVERSARY"));
    }

    @Test void surpriseIsAuthorEditableOnlyEvenWhenRecipientTriesChangingKind() throws Exception {
        UUID id = UUID.randomUUID();
        save(author, id, card("SURPRISE", "2026-10-04")).andExpect(status().isOk());
        save(partner, id, card("SURPRISE", "2026-10-05")).andExpect(status().isForbidden());
        save(partner, id, card("BIRTHDAY", "2026-10-05")).andExpect(status().isForbidden());
        remove(partner, id).andExpect(status().isForbidden());
        save(author, id, card("SURPRISE", "2026-10-05")).andExpect(status().isOk());
        remove(author, id).andExpect(status().isNoContent());
    }

    @Test void recipientCannotPrivatizePartnersSharedCardByConvertingItToSurprise() throws Exception {
        UUID id = UUID.randomUUID();
        save(author, id, card("BIRTHDAY", "2026-10-04")).andExpect(status().isOk());
        var surprise = card("SURPRISE", "2026-10-04"); surprise.put("recipientId", author);
        save(partner, id, surprise).andExpect(status().isForbidden());
    }

    @Test void surpriseRequiresActiveOtherMemberAndAnnualRepeatIsOptional() throws Exception {
        for (UUID recipient : new UUID[]{author, outsider}) {
            var body = card("SURPRISE", "2026-10-04"); body.put("recipientId", recipient);
            save(author, UUID.randomUUID(), body).andExpect(status().isBadRequest());
        }
        var missing = card("SURPRISE", "2026-10-04"); missing.remove("recipientId");
        save(author, UUID.randomUUID(), missing).andExpect(status().isBadRequest());
        db.update("UPDATE couple_member SET left_at=now() WHERE couple_id=? AND user_id=?", space, partner);
        save(author, UUID.randomUUID(), card("SURPRISE", "2026-10-04")).andExpect(status().isBadRequest());
        db.update("UPDATE couple_member SET left_at=NULL WHERE couple_id=? AND user_id=?", space, partner);
        db.update("UPDATE app_user SET status='DISABLED' WHERE id=?", partner);
        save(author, UUID.randomUUID(), card("SURPRISE", "2026-10-04")).andExpect(status().isBadRequest());
        db.update("UPDATE app_user SET status='ACTIVE' WHERE id=?", partner);
        var once = card("SURPRISE", "2026-10-04"); once.remove("repeatsYearly");
        save(author, UUID.randomUUID(), once).andExpect(status().isOk()).andExpect(jsonPath("$.repeatsYearly").value(false));
    }

    @Test void futureSurpriseIsHiddenUntilShanghaiScheduledDayIncludingItsPhoto() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        UUID id = UUID.randomUUID(), photo = asset(author, space, "IMAGE", "READY");
        var body = card("SURPRISE", today.plusDays(1).toString()); body.put("photoAssetId", photo);
        save(author, id, body).andExpect(status().isOk());
        list(author).andExpect(jsonPath("$.length()").value(1));
        list(partner).andExpect(jsonPath("$.length()").value(0));
        assertThatThrownBy(() -> media.readAsset(partner, photo)).hasMessage("media is not shared");
        body.put("date", today.toString());
        save(author, id, body).andExpect(status().isOk());
        list(partner).andExpect(jsonPath("$[0].body").value("A private message"));
        assertThatCode(() -> media.readAsset(partner, photo)).doesNotThrowAnyException();
        remove(author, id).andExpect(status().isNoContent());
        list(partner).andExpect(jsonPath("$.length()").value(0));
        assertThatThrownBy(() -> media.readAsset(partner, photo)).hasMessage("media is not shared");
        assertThatCode(() -> media.readAsset(author, photo)).doesNotThrowAnyException();
    }

    @Test void deletedCardsRemainTombstonesAndRetryCannotResurrect() throws Exception {
        UUID id = UUID.randomUUID(); var body = card("BIRTHDAY", "2026-10-04");
        save(author, id, body).andExpect(status().isOk());
        remove(partner, id).andExpect(status().isNoContent());
        remove(author, id).andExpect(status().isNoContent());
        save(author, id, body).andExpect(status().isGone());
        assertThat(db.queryForObject("SELECT deleted_at IS NOT NULL FROM occasion WHERE id=?", Boolean.class, id)).isTrue();
        list(partner).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void photoMustBeReadyImageInSameSpaceAndActuallyReadableBeforeReference() throws Exception {
        for (UUID photo : new UUID[]{asset(author, space, "IMAGE", "UPLOADING"),
                asset(author, space, "AUDIO", "READY"), asset(author, otherSpace, "IMAGE", "READY"),
                asset(partner, space, "IMAGE", "READY"), UUID.randomUUID()}) {
            var body = card("BIRTHDAY", "2026-10-04"); body.put("photoAssetId", photo);
            save(author, UUID.randomUUID(), body).andExpect(status().is4xxClientError());
        }
        UUID photo = asset(author, space, "IMAGE", "READY"), id = UUID.randomUUID();
        var shared = card("BIRTHDAY", "2026-10-04"); shared.put("photoAssetId", photo);
        save(author, id, shared).andExpect(status().isOk());
        assertThatCode(() -> media.readAsset(partner, photo)).doesNotThrowAnyException();
        // Read permission from a live shared occasion is sufficient for reuse.
        save(partner, UUID.randomUUID(), shared).andExpect(status().isOk());
        db.update("UPDATE occasion SET deleted_at=now() WHERE couple_id=?", space);
        assertThatThrownBy(() -> media.readAsset(partner, photo)).hasMessage("media is not shared");
    }

    @Test void deletedAlbumPhotoCannotLaunderPrivateMediaIntoAnOccasion() throws Exception {
        UUID photo = asset(partner, space, "IMAGE", "READY"), album = UUID.randomUUID();
        db.update("INSERT INTO album(id,couple_id,title,created_by) VALUES (?,?,'Album',?)", album, space, partner);
        db.update("INSERT INTO album_item(album_id,media_asset_id,order_key,added_by,deleted_at) VALUES (?,?,0,?,now())",
                album, photo, partner);
        var body = card("BIRTHDAY", "2026-10-04"); body.put("photoAssetId", photo);
        save(author, UUID.randomUUID(), body).andExpect(status().isForbidden());
    }

    @Test void originalBytesAreDownloadedOnlyWhileSurpriseIsVisibleAndLive() throws Exception {
        UUID id = UUID.randomUUID(), photo = asset(author, space, "IMAGE", "READY");
        byte[] bytes = new byte[]{1, 2, 3, 4};
        ((LocalObjectStorage)storage).put("occasion-test/" + photo, bytes);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var body = card("SURPRISE", today.plusDays(1).toString()); body.put("photoAssetId", photo);
        save(author, id, body).andExpect(status().isOk());
        mvc.perform(get("/api/v1/media/{id}/data", photo).with(TestAuth.deviceSession(partner, space)))
                .andExpect(status().isForbidden());
        body.put("date", today.toString()); save(author, id, body).andExpect(status().isOk());
        mvc.perform(get("/api/v1/media/{id}/data", photo).with(TestAuth.deviceSession(partner, space)))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
        mvc.perform(get("/api/v1/media/{id}/data", photo).with(TestAuth.deviceSession(outsider, otherSpace)))
                .andExpect(status().isForbidden());
        remove(author, id).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/media/{id}/data", photo).with(TestAuth.deviceSession(partner, space)))
                .andExpect(status().isForbidden());
    }

    @Test void invalidTextKindAndCalendarDateAreRejectedAndLimitsAreAccepted() throws Exception {
        for (var override : new Object[][]{{"title", " "}, {"title", "x".repeat(81)},
                {"body", "x".repeat(4001)}, {"kind", "UNKNOWN"}, {"date", "2026-02-29"},
                {"date", "2026-04-31"}, {"date", "04/10/2026"}, {"date", null}}) {
            var body = card("BIRTHDAY", "2026-10-04"); body.put((String)override[0], override[1]);
            save(author, UUID.randomUUID(), body).andExpect(status().isBadRequest());
        }
        var max = card("BIRTHDAY", "2028-02-29"); max.put("title", "x".repeat(80)); max.put("body", "x".repeat(4000));
        save(author, UUID.randomUUID(), max).andExpect(status().isOk());
        var empty = card("BIRTHDAY", "2026-10-04"); empty.put("body", ""); empty.remove("repeatsYearly");
        save(author, UUID.randomUUID(), empty).andExpect(status().isOk()).andExpect(jsonPath("$.repeatsYearly").value(true));
    }
}
