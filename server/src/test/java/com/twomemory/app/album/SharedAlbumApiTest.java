package com.twomemory.app.album;

import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.auth.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL + controller tests: albums stay independent of diary entries. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class SharedAlbumApiTest {
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabaseConfig::applicationUrl);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    UUID space, owner, partner, outsider, album;
    String root;

    @BeforeEach void setup() {
        space = UUID.randomUUID(); owner = UUID.randomUUID(); partner = UUID.randomUUID();
        outsider = UUID.randomUUID(); album = UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?, 'ACTIVE')", space);
        for (UUID user : new UUID[]{owner, partner, outsider})
            db.update("INSERT INTO app_user(id,status) VALUES (?, 'ACTIVE')", user);
        for (UUID user : new UUID[]{owner, partner})
            db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", space, user);
        root = "/api/v1/couple/" + space + "/albums";
    }
    void create() throws Exception {
        mvc.perform(put(root + "/" + album).with(TestAuth.deviceSession(owner, space))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"一起旅行\",\"category\":\"旅行\"}"))
                .andExpect(status().isOk());
    }
    UUID asset(String status, UUID assetSpace) {
        UUID id = UUID.randomUUID();
        db.update("""
            INSERT INTO media_asset(id,couple_id,owner_id,kind,status,object_key,mime_type,byte_size,sha256,width,height)
            VALUES (?,?,?,'IMAGE',?::media_status,?,'image/png',10,?,20,30)
            """, id, assetSpace, owner, status, "couples/" + assetSpace + "/assets/" + id, "a".repeat(64));
        return id;
    }
    @Test void partnerReadsCategorizedAlbumAndReadyPhotoWithoutCreatingDiaryEntry() throws Exception {
        create();
        UUID media = asset("READY", space); UUID photo = UUID.randomUUID();
        db.update("UPDATE media_asset SET owner_id=? WHERE id=?", partner, media);
        String payload = "{\"assetId\":\"" + media + "\",\"takenAtEpochMillis\":1234}";
        for (int retry = 0; retry < 2; retry++)
            mvc.perform(put(root + "/" + album + "/photos/" + photo)
                    .with(TestAuth.deviceSession(partner, space)).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isOk());
        mvc.perform(get(root).with(TestAuth.deviceSession(partner, space)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("一起旅行"))
                .andExpect(jsonPath("$[0].category").value("旅行"))
                .andExpect(jsonPath("$[0].photos.length()").value(1))
                .andExpect(jsonPath("$[0].photos[0].id").value(photo.toString()))
                .andExpect(jsonPath("$[0].photos[0].width").value(20));
        assertThat(db.queryForObject("SELECT count(*) FROM entry WHERE couple_id=?", Integer.class, space)).isZero();
    }
    @Test void outsiderCannotReadOrCreateInThisSpace() throws Exception {
        mvc.perform(get(root).with(TestAuth.deviceSession(outsider, space))).andExpect(status().isForbidden());
        mvc.perform(put(root + "/" + album).with(TestAuth.deviceSession(outsider, space))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"偷看\",\"category\":\"旅行\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void unverifiedAndOtherSpacePhotosAreRejected() throws Exception {
        create();
        UUID otherSpace = UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?, 'ACTIVE')", otherSpace);
        for (UUID id : new UUID[]{asset("UPLOADING", space), asset("READY", otherSpace)})
            mvc.perform(put(root + "/" + album + "/photos/" + UUID.randomUUID())
                    .with(TestAuth.deviceSession(owner, space)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"assetId\":\"" + id + "\",\"takenAtEpochMillis\":1234}"))
                    .andExpect(status().isConflict());
    }
    @Test void deletionKeepsTombstoneAndRetryCannotResurrectAlbum() throws Exception {
        create();
        mvc.perform(delete(root + "/" + album).with(TestAuth.deviceSession(partner, space)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(root + "/" + album).with(TestAuth.deviceSession(owner, space)))
                .andExpect(status().isNoContent());
        mvc.perform(get(root).with(TestAuth.deviceSession(partner, space)))
                .andExpect(jsonPath("$[0].deleted").value(true));
        mvc.perform(put(root + "/" + album).with(TestAuth.deviceSession(owner, space))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"一起旅行\",\"category\":\"旅行\"}"))
                .andExpect(status().isGone());
    }
    @Test void coverSelectionIsSharedAndCannotReferenceAnotherAlbum() throws Exception {
        create();
        UUID first = asset("READY", space), second = asset("READY", space);
        for (UUID media : new UUID[]{first, second})
            mvc.perform(put(root + "/" + album + "/photos/" + UUID.randomUUID())
                    .with(TestAuth.deviceSession(owner, space)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"assetId\":\"" + media + "\",\"takenAtEpochMillis\":1234}"))
                    .andExpect(status().isOk());
        mvc.perform(put(root + "/" + album + "/cover").with(TestAuth.deviceSession(partner, space))
                .contentType(MediaType.APPLICATION_JSON).content("{\"assetId\":\"" + second + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(root).with(TestAuth.deviceSession(owner, space)))
                .andExpect(jsonPath("$[0].coverAssetId").value(second.toString()));
        UUID unrelated = asset("READY", space);
        mvc.perform(put(root + "/" + album + "/cover").with(TestAuth.deviceSession(owner, space))
                .contentType(MediaType.APPLICATION_JSON).content("{\"assetId\":\"" + unrelated + "\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void photoDeletionRetainsOriginalAndIsVisibleToPartner() throws Exception {
        create(); UUID media = asset("READY", space); UUID photo = UUID.randomUUID();
        String path = root + "/" + album + "/photos/" + photo;
        mvc.perform(put(path).with(TestAuth.deviceSession(owner, space)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assetId\":\"" + media + "\",\"takenAtEpochMillis\":1234}"))
                .andExpect(status().isOk());
        mvc.perform(delete(path).with(TestAuth.deviceSession(partner, space))).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(TestAuth.deviceSession(owner, space))).andExpect(status().isNoContent());
        mvc.perform(get(root).with(TestAuth.deviceSession(partner, space)))
                .andExpect(jsonPath("$[0].photos[0].deleted").value(true))
                .andExpect(jsonPath("$[0].coverAssetId").isEmpty());
        assertThat(db.queryForObject("SELECT status::text FROM media_asset WHERE id=?", String.class, media))
                .isEqualTo("READY");
        mvc.perform(put(path).with(TestAuth.deviceSession(owner, space)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assetId\":\"" + media + "\",\"takenAtEpochMillis\":1234}"))
                .andExpect(status().isGone());
    }
    @Test void deletingNonCoverPhotoDoesNotReplaceChosenCover() throws Exception {
        create();
        UUID[] assets = {asset("READY", space), asset("READY", space), asset("READY", space)};
        UUID[] photos = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        for (int index = 0; index < 3; index++)
            mvc.perform(put(root + "/" + album + "/photos/" + photos[index])
                    .with(TestAuth.deviceSession(owner, space)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"assetId\":\"" + assets[index] + "\",\"takenAtEpochMillis\":1234}"))
                    .andExpect(status().isOk());
        mvc.perform(put(root + "/" + album + "/cover").with(TestAuth.deviceSession(owner, space))
                .contentType(MediaType.APPLICATION_JSON).content("{\"assetId\":\"" + assets[2] + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete(root + "/" + album + "/photos/" + photos[1])
                .with(TestAuth.deviceSession(owner, space))).andExpect(status().isNoContent());
        mvc.perform(get(root).with(TestAuth.deviceSession(partner, space)))
                .andExpect(jsonPath("$[0].coverAssetId").value(assets[2].toString()));
    }
}
