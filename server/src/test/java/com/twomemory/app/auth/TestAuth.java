package com.twomemory.app.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

/**
 * Test-only factory for authenticated device-session principals.
 *
 * Sets both the SecurityContextHolder and the TestSecurityContextHolder so
 * {@code @AuthenticationPrincipal} resolves even in MockMvc slices built with
 * {@code addFilters = false} (no security filter chain in the pipeline).
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor deviceSession(UUID userId, UUID coupleId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, coupleId, null);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        return request -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            TestSecurityContextHolder.setContext(context);
            SecurityContextHolder.setContext(context);
            request.setUserPrincipal(principal);
            return request;
        };
    }
}
