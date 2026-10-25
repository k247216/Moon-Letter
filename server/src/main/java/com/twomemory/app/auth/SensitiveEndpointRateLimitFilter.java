package com.twomemory.app.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance, bounded abuse guard. Never treats client Forwarded headers as identity. */
public final class SensitiveEndpointRateLimitFilter extends OncePerRequestFilter {
    private final Clock clock;
    private final Map<String,Integer> attempts = new HashMap<>();
    private long window = Long.MIN_VALUE;
    private int total;
    public SensitiveEndpointRateLimitFilter(Clock clock) { this.clock=clock; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return true;
        String path=request.getServletPath();
        if(path.isEmpty()) path=request.getRequestURI();
        return !(path.equals("/api/v1/bootstrap") || path.equals("/api/v1/couple") || path.equals("/api/v1/couple/pair") ||
            path.matches("/api/v1/couple/[^/]+/pairing-token") || path.startsWith("/api/v1/sessions"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws IOException,ServletException {
        // All sensitive operations share a source budget: UUID/path rotation cannot create new buckets.
        if (!allow(request.getRemoteAddr())) {
            response.setStatus(429); response.setHeader("Retry-After","60");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Please retry later\"}");
            return;
        }
        chain.doFilter(request,response);
    }
    private synchronized boolean allow(String key) {
        long now=clock.millis()/60_000;
        if(now!=window) { window=now; attempts.clear(); total=0; }
        int count=attempts.getOrDefault(key,0);
        if(count>=10 || total>=60 || (!attempts.containsKey(key)&&attempts.size()>=1024)) return false;
        attempts.put(key,count+1); total++; return true;
    }
}
