package com.twomemory.app.capsule;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/couple/{coupleId}/capsules")
public class CapsuleController {
    private final CapsuleService service;

    public CapsuleController(CapsuleService service) { this.service = service; }

    @GetMapping
    public List<CapsuleView> list(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID coupleId) {
        return service.list(userId(actor), coupleId);
    }

    @PutMapping("/{id}")
    public CapsuleView save(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID coupleId,
                            @PathVariable UUID id, @RequestBody SaveCapsuleRequest request) {
        return service.save(userId(actor), coupleId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @PathVariable UUID coupleId, @PathVariable UUID id) {
        service.delete(userId(actor), coupleId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(AuthenticatedUser actor) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
        return actor.userId();
    }
}
