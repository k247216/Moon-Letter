package com.twomemory.app.entry;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
public class PrivacyController {
    @GetMapping("/api/v1/privacy/capabilities")
    public Map<String, Boolean> capabilities(@AuthenticationPrincipal AuthenticatedUser actor) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
        return Map.of("privateEntries", true, "privateOccasions", true);
    }
}
