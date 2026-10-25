package com.twomemory.app.capsule;

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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CapsuleAuthenticationTest {
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabaseConfig::applicationUrl);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @Test void realDeviceBearerControlsAuthorAndRevokedSessionsLoseAccess() throws Exception {
        UUID couple = UUID.randomUUID(), author = UUID.randomUUID(), partner = UUID.randomUUID(), id = UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", couple);
        for (UUID user : new UUID[]{author, partner}) {
            db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", user);
            db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", couple, user);
        }
        String authorToken = session(author, couple), partnerToken = session(partner, couple);
        String root = "/api/v1/couple/" + couple + "/capsules";
        String body = "{\"title\":\"Envelope\",\"body\":\"sealed secret\",\"visibility\":\"SHARED\",\"unlockDate\":\"9999-12-31\",\"authorId\":\"" + partner + "\"}";
        mvc.perform(get(root)).andExpect(status().isUnauthorized());
        mvc.perform(put(root + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(delete(root + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(put(root + "/" + id).header("Authorization", "Bearer " + authorToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorId").value(author.toString()));
        mvc.perform(get(root).header("Authorization", "Bearer " + partnerToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").isEmpty());
        mvc.perform(put(root + "/" + id).header("Authorization", "Bearer " + partnerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(delete(root + "/" + id).header("Authorization", "Bearer " + partnerToken)).andExpect(status().isForbidden());
        db.update("UPDATE device_session SET revoked_at=now() WHERE user_id=?", author);
        mvc.perform(get(root).header("Authorization", "Bearer " + authorToken)).andExpect(status().isUnauthorized());
        mvc.perform(put(root + "/" + id).header("Authorization", "Bearer " + authorToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(delete(root + "/" + id).header("Authorization", "Bearer " + authorToken)).andExpect(status().isUnauthorized());
    }

    private String session(UUID actor, UUID couple) throws Exception {
        String token = UUID.randomUUID().toString();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        db.update("INSERT INTO device_session(id,user_id,couple_id,token_hash) VALUES (?,?,?,?)", UUID.randomUUID(), actor, couple, hash);
        return token;
    }
}
