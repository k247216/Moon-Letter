package com.twomemory.app.auth;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class DeviceTokenInputBoundsTest {
    @Test void oversizedTokenIsRejectedBeforeDatabaseLookup() throws Exception { rejectsWithoutLookup("x".repeat(2000)); }
    @Test void emptyTokenIsRejectedBeforeDatabaseLookup() throws Exception { rejectsWithoutLookup("   "); }
    private void rejectsWithoutLookup(String token) throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        var request=new MockHttpServletRequest("GET","/api/v1/couple/test");
        request.addHeader("Authorization","Bearer "+token);
        var response=new MockHttpServletResponse();
        new DeviceSessionAuthenticationFilter(jdbc).doFilter(request,response,(req,res)->{throw new AssertionError("Unauthorized input forwarded");});
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(jdbc);
    }
}
