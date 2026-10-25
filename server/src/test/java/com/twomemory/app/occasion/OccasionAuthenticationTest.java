package com.twomemory.app.occasion;

import com.twomemory.app.TestDatabaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Route verification through the real device-session and Spring Security filters. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OccasionAuthenticationTest {
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabaseConfig::applicationUrl);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @Test void bearerIsRequiredAndAuthenticatedPrincipalControlsAuthor() throws Exception {
        UUID couple = UUID.randomUUID(), actor = UUID.randomUUID(), id = UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", couple);
        db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", actor);
        db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", couple, actor);
        db.update("INSERT INTO device_session(id,user_id,couple_id,token_hash) VALUES (?,?,?,?)",
                UUID.randomUUID(), actor, couple, hash);
        String root = "/api/v1/couple/" + couple + "/occasions";
        String body = "{\"kind\":\"BIRTHDAY\",\"title\":\"Birthday\",\"date\":\"2026-10-04\"}";
        mvc.perform(get("/api/v1/privacy/capabilities")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/privacy/capabilities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.privateEntries").value(true))
                .andExpect(jsonPath("$.privateOccasions").value(true));
        mvc.perform(get(root)).andExpect(status().isUnauthorized());
        mvc.perform(put(root + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(root + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(put(root + "/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(actor.toString()));
        mvc.perform(get(root).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        db.update("UPDATE app_user SET status='DISABLED' WHERE id=?", actor);
        mvc.perform(get(root).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        db.update("UPDATE app_user SET status='ACTIVE' WHERE id=?", actor);
        mvc.perform(delete(root + "/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
    }
}
