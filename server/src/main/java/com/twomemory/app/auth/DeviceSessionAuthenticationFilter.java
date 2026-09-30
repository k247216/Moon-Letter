package com.twomemory.app.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Resolves Authorization: Bearer tokens to device sessions. Only the SHA-256
 * hash is stored server side; the raw token never reaches the database.
 *
 * Public endpoints (health, bootstrap, pairing) are skipped entirely.
 * On a protected endpoint a missing or invalid token is answered with a
 * direct 401 response — never {@code sendError}, otherwise the container
 * forwards to /error, the security chain re-secures that dispatch and the
 * status silently degrades to 403.
 */
public class DeviceSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/actuator/health",
            "/api/v1/bootstrap",
            "/api/v1/pair",
            // Boot's error dispatch must not be re-secured, otherwise the
            // original status (409/401/...) is replaced by a 403.
            "/error");

    private final JdbcTemplate jdbcTemplate;

    public DeviceSessionAuthenticationFilter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response, "missing bearer token");
            return;
        }
        String rawToken = header.substring("Bearer ".length()).trim();
        String tokenHash = sha256Hex(rawToken);

        List<AuthenticatedUser> matches = jdbcTemplate.query("""
                SELECT s.id AS session_id, s.user_id, s.couple_id
                FROM device_session s
                WHERE s.token_hash = ? AND s.revoked_at IS NULL
                """, (resultSet, rowNum) -> new AuthenticatedUser(
                UUID.fromString(resultSet.getString("user_id")),
                resultSet.getString("couple_id") == null
                        ? null : UUID.fromString(resultSet.getString("couple_id")),
                UUID.fromString(resultSet.getString("session_id"))), tokenHash);

        if (matches.size() != 1) {
            reject(response, "invalid bearer token");
            return;
        }
        AuthenticatedUser user = matches.getFirst();
        jdbcTemplate.update("UPDATE device_session SET last_used_at = now() WHERE id = ?", user.sessionId());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
        response.getWriter().flush();
    }

    static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
