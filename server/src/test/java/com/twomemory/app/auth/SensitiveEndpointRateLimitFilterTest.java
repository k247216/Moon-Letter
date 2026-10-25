package com.twomemory.app.auth;

import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class SensitiveEndpointRateLimitFilterTest {
    @Test void excessAttemptsAreRejectedEvenWithForgedForwardedAddressesAndResumeNextMinute() throws Exception {
        var instant = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-05T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone){return this;}
            public Instant instant(){return instant.get();}
        };
        var filter = new SensitiveEndpointRateLimitFilter(clock);
        var passed = new AtomicInteger();
        for(int i=0;i<11;i++) {
            var request = request("/api/v1/couple/pair", "192.0.2.1");
            request.addHeader("X-Forwarded-For", "192.0.2."+(i+2));
            var response = new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->passed.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(i<10?200:429);
            if(i==10) assertThat(response.getHeader("Retry-After")).isNotBlank();
        }
        assertThat(passed.get()).isEqualTo(10);
        instant.set(instant.get().plusSeconds(61));
        var response = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/couple/pair","192.0.2.1"),response,(req,res)->passed.incrementAndGet());
        assertThat(passed.get()).isEqualTo(11);
    }
    @Test void ordinaryRecordRequestsDoNotConsumeSensitiveBudget() throws Exception {
        var filter = new SensitiveEndpointRateLimitFilter(Clock.systemUTC());
        var passed = new AtomicInteger();
        for(int i=0;i<100;i++) filter.doFilter(request("/api/v1/sync/pull","192.0.2.1"),new MockHttpServletResponse(),(req,res)->passed.incrementAndGet());
        assertThat(passed.get()).isEqualTo(100);
    }
    @Test void rotatingSourceAddressesCannotBypassGlobalBudget() throws Exception {
        var filter = new SensitiveEndpointRateLimitFilter(Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"),ZoneOffset.UTC));
        var passed = new AtomicInteger();
        for(int i=0;i<61;i++) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/bootstrap","192.0.2."+i),response,(req,res)->passed.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(i<60?200:429);
        }
        assertThat(passed.get()).isEqualTo(60);
    }
    @Test void rotatingSpaceIdsCannotBypassOneSourcesPairingBudget() throws Exception {
        var filter = new SensitiveEndpointRateLimitFilter(Clock.systemUTC());
        for (int i=0;i<11;i++) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/couple/"+java.util.UUID.randomUUID()+"/pairing-token", "192.0.2.1"),response,(req,res)->{});
            assertThat(response.getStatus()).isEqualTo(i<10?200:429);
        }
    }
    private MockHttpServletRequest request(String path,String remote) {
        var request=new MockHttpServletRequest("POST",path); request.setRemoteAddr(remote); return request;
    }
}
