package com.twomemory.app.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-time deployment bootstrap. Only callable while the installation has no
 * users and only with the configured BOOTSTRAP_SECRET.
 */
@RestController
@RequestMapping("/api/v1/bootstrap")
public class BootstrapController {

    private final BootstrapService bootstrapService;

    public BootstrapController(BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @PostMapping
    public ResponseEntity<BootstrapResult> bootstrap(
            @RequestHeader(value = "X-Bootstrap-Secret", required = false) String secret,
            @RequestBody BootstrapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bootstrapService.bootstrap(secret, request.displayName()));
    }

    public record BootstrapRequest(String displayName) {
    }

    public record BootstrapResult(String userId, String coupleId, String token) {
    }
}
