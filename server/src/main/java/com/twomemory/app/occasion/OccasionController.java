package com.twomemory.app.occasion;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/couple/{coupleId}/occasions")
public class OccasionController {
    private final OccasionService service;
    public OccasionController(OccasionService service) { this.service = service; }

    @GetMapping
    public List<OccasionView> list(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID coupleId) {
        return service.list(userId(actor), coupleId);
    }
    @GetMapping("/{id}")
    public OccasionView read(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID coupleId, @PathVariable UUID id) {
        return service.list(userId(actor), coupleId).stream().filter(card -> card.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "occasion not found"));
    }

    @PutMapping("/{id}")
    public OccasionView save(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID coupleId,
                             @PathVariable UUID id, @RequestBody SaveOccasionRequest request) {
        return service.save(userId(actor), coupleId, id, request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID coupleId, @PathVariable UUID id) {
        service.delete(userId(actor), coupleId, id);
        return ResponseEntity.noContent().build();
    }
    private UUID userId(AuthenticatedUser actor) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
        return actor.userId();
    }
}
