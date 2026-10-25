package com.twomemory.app.couple;

import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.auth.DeviceSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Profile validation through real HTTP authentication; all assets are synthetic harness rows. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AvatarAssetValidationTest {
    private static final String TEST_DB = "moon_letter_avatar_validation_test";

    static {
        // Same dedicated database setup as the existing replacement pairing harness.
        try (Connection connection = DriverManager.getConnection(TestDatabaseConfig.adminUrl(),
                TestDatabaseConfig.user(), TestDatabaseConfig.password());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception failure) {
            throw new IllegalStateException("could not prepare isolated avatar validation test database", failure);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
    }

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate db;
    @Autowired private DeviceSessionService sessions;
    private UUID actor;
    private UUID partner;
    private UUID space;
    private UUID originalAvatar;
    private String actorToken;

    @BeforeEach
    void seedIsolatedMembersAndExistingAvatar() {
        assertThat(db.queryForObject("SELECT current_database()", String.class)).isEqualTo(TEST_DB);
        actor = UUID.randomUUID();
        partner = UUID.randomUUID();
        space = UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", space);
        for (UUID member : new UUID[]{actor, partner}) {
            db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", member);
            db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", space, member);
            db.update("INSERT INTO user_profile(user_id,display_name,theme) VALUES (?,'fixture-member','WARM_BEIGE')", member);
        }
        originalAvatar = asset(space, actor, "IMAGE", "READY", false);
        db.update("UPDATE user_profile SET avatar_asset_id=? WHERE user_id=?", originalAvatar, actor);
        actorToken = sessions.issueSession(actor, space);
    }

    @ParameterizedTest(name = "reject owned {0} avatar in {1} state")
    @CsvSource({
            "IMAGE,UPLOADING",
            "IMAGE,PROCESSING",
            "IMAGE,FAILED",
            "AUDIO,READY",
            "VIDEO,READY"
    })
    void avatarMustBeAReadyImage(String kind, String status) {
        UUID invalid = asset(space, actor, kind, status, false);
        assertRejectedWithoutChangingProfile(invalid);
    }

    @Test
    void readyImageFromAnotherSpaceCannotBecomeAvatar() {
        UUID otherSpace = UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'UNPAIRED')", otherSpace);
        UUID invalid = asset(otherSpace, actor, "IMAGE", "READY", false);
        assertRejectedWithoutChangingProfile(invalid);
    }

    @Test
    void partnersReadyImageCannotBecomeOwnAvatar() {
        UUID invalid = asset(space, partner, "IMAGE", "READY", false);
        assertRejectedWithoutChangingProfile(invalid);
    }

    @Test
    void deletedReadyImageCannotBecomeAvatar() {
        UUID invalid = asset(space, actor, "IMAGE", "READY", true);
        assertRejectedWithoutChangingProfile(invalid);
    }

    @Test
    void readyOwnImageInCurrentSpaceBecomesAvatarAndCanBeRetried() {
        UUID valid = asset(space, actor, "IMAGE", "READY", false);
        for (int attempt = 0; attempt < 2; attempt++) {
            ResponseEntity<ProfileView> response = changeAvatar(valid);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            ProfileView profile = response.getBody();
            assertThat(profile).isNotNull();
            assertThat(profile.avatarAssetId()).isEqualTo(valid);
            assertThat(profile.userId()).isEqualTo(actor);
            assertThat(profile.displayName()).isEqualTo("avatar-update");
            assertThat(storedAvatar()).isEqualTo(valid);
        }
    }

    private void assertRejectedWithoutChangingProfile(UUID invalid) {
        ResponseEntity<ProfileView> response = changeAvatar(invalid);
        assertThat(response.getStatusCode().is4xxClientError())
                .as("invalid avatar must be rejected; HTTP status was %s", response.getStatusCode()).isTrue();
        assertThat(storedAvatar()).as("rejection must retain the existing avatar").isEqualTo(originalAvatar);
        String displayName = db.queryForObject("SELECT display_name FROM user_profile WHERE user_id=?", String.class, actor);
        assertThat(displayName).isEqualTo("fixture-member");
    }

    private UUID storedAvatar() {
        return db.queryForObject("SELECT avatar_asset_id FROM user_profile WHERE user_id=?", UUID.class, actor);
    }

    private ResponseEntity<ProfileView> changeAvatar(UUID avatar) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(actorToken);
        return http.exchange("/api/v1/couple/" + space + "/members/" + actor + "/profile",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("avatarAssetId", avatar,
                        "displayName", "avatar-update"), headers), ProfileView.class);
    }

    private UUID asset(UUID assetSpace, UUID owner, String kind, String status, boolean deleted) {
        UUID id = UUID.randomUUID();
        String mime = switch (kind) {
            case "IMAGE" -> "image/jpeg";
            case "AUDIO" -> "audio/mp4";
            case "VIDEO" -> "video/mp4";
            default -> throw new IllegalArgumentException("unsupported synthetic asset kind");
        };
        db.update("""
                INSERT INTO media_asset(id,couple_id,owner_id,kind,status,object_key,mime_type,
                                        byte_size,sha256,deleted_at)
                VALUES (?,?,?,?::media_kind,?::media_status,?,?,1,?,?)
                """, id, assetSpace, owner, kind, status, "avatar-validation-fixture/" + id,
                mime, "a".repeat(64), deleted ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
        return id;
    }
}
