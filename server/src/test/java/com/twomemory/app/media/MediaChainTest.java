package com.twomemory.app.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 13 functional chain: an image asset is created, its bytes are
 * uploaded through the local-storage endpoint, the asset becomes READY, the
 * partner downloads the exact same bytes, and the space exports as JSON.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MediaChainTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_media_test";
    private static final String TOKEN_A = "media-test-token-a";
    private static final String TOKEN_B = "media-test-token-b";

    static UUID userA;
    static UUID userB;
    static UUID coupleId;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
        registry.add("spring.datasource.username", () -> ADMIN_USER);
        registry.add("spring.datasource.password", () -> ADMIN_PASSWORD);
        registry.add("moon-letter.bootstrap.secret", () -> "media-test-bootstrap-secret");
        registry.add("moon-letter.storage.local-dir", () -> "./target/media-test-storage");
    }

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated media test database", exception);
        }
    }

    @Test
    void imageUploadDownloadAndExportRoundTrip() throws Exception {
        // 0. Seed a member pair through the real endpoints.
        ResponseEntity<Map> boot = postBootstrap("小满");
        assertThat(boot.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tokenA = boot.getBody().get("token").toString();
        userA = UUID.fromString(boot.getBody().get("userId").toString());
        coupleId = UUID.fromString(boot.getBody().get("coupleId").toString());
        String pairingToken = exchangeWithBearer("/api/v1/couple/" + coupleId + "/pairing-token",
                tokenA, HttpMethod.POST).getBody().get("pairingToken").toString();
        ResponseEntity<Map> paired = restTemplate.postForEntity("/api/v1/couple/pair",
                Map.of("token", pairingToken), Map.class);
        assertThat(paired.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tokenB = paired.getBody().get("deviceToken").toString();

        // 1. A requests an upload ticket for a PNG image.
        byte[] imageBytes = "fake-png-bytes-for-chain-test".getBytes(StandardCharsets.UTF_8);
        String sha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(imageBytes));
        Map<String, Object> createCommand = Map.of(
                "ownerId", userA.toString(),
                "coupleId", coupleId.toString(),
                "operationId", UUID.randomUUID().toString(),
                "kind", "IMAGE",
                "mimeType", "image/png",
                "byteSize", imageBytes.length,
                "width", 100,
                "height", 80,
                "sha256", sha256);
        ResponseEntity<Map> ticket = exchangeWithBearer("/api/v1/media/uploads", tokenA,
                HttpMethod.POST, createCommand);
        assertThat(ticket.getStatusCode())
                .as("ticket body: %s", ticket.getBody())
                .isEqualTo(HttpStatus.CREATED);
        UUID assetId = UUID.fromString(ticket.getBody().get("assetId").toString());

        // 2. A uploads the raw bytes; the asset becomes READY.
        HttpHeaders dataHeaders = new HttpHeaders();
        dataHeaders.setBearerAuth(tokenA);
        dataHeaders.set("Content-Type", "image/png");
        ResponseEntity<Map> uploaded = restTemplate.exchange("/api/v1/media/" + assetId + "/data",
                HttpMethod.POST, new HttpEntity<>(imageBytes, dataHeaders), Map.class);
        assertThat(uploaded.getStatusCode())
                .as("upload body: %s", uploaded.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(uploaded.getBody().get("id")).isEqualTo(assetId.toString());

        // 3. The partner downloads the exact same bytes.
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(tokenB);
        ResponseEntity<byte[]> downloaded = restTemplate.exchange("/api/v1/media/" + assetId + "/data",
                HttpMethod.GET, new HttpEntity<>(getHeaders), byte[].class);
        assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(downloaded.getBody()).isEqualTo(imageBytes);

        // 4. A third member outside the space is rejected.
        // (covered by requireMember: skipped here, tested in Task 4 tests)

        // 5. Export delivers readable JSON with the space id, no secrets.
        HttpHeaders exportHeaders = new HttpHeaders();
        exportHeaders.setBearerAuth(tokenA);
        ResponseEntity<String> exported = restTemplate.exchange(
                "/api/v1/export?coupleId=" + coupleId, HttpMethod.GET,
                new HttpEntity<>(exportHeaders), String.class);
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = exported.getBody();
        assertThat(body).contains("\"coupleId\" : \"" + coupleId + "\"");
        assertThat(body).doesNotContain("token", "secret", "password");
    }

    private ResponseEntity<Map> postBootstrap(String displayName) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Bootstrap-Secret", "media-test-bootstrap-secret");
        return restTemplate.postForEntity("/api/v1/bootstrap",
                new HttpEntity<>(Map.of("displayName", displayName), headers), Map.class);
    }

    private ResponseEntity<Map> exchangeWithBearer(String path, String token, HttpMethod method,
                                                   Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, method,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), Map.class);
    }

    private ResponseEntity<Map> exchangeWithBearer(String path, String token, HttpMethod method) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, method, new HttpEntity<>(headers), Map.class);
    }
}
