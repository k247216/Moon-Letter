package com.twomemory.app.album;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/couple/{space}/albums")
public class SharedAlbumController {
    private final SharedAlbumService service;
    public SharedAlbumController(SharedAlbumService service) { this.service = service; }
    @GetMapping public List<SharedAlbumService.Album> read(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID space) { return service.read(actor.userId(), space); }
    @PutMapping("/{album}") public ResponseEntity<Void> save(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID space, @PathVariable UUID album, @RequestBody SharedAlbumService.SaveAlbum request) {
        service.save(actor.userId(), space, album, request); return ResponseEntity.ok().build();
    }
    @PutMapping("/{album}/photos/{photo}") public ResponseEntity<Void> addPhoto(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID space,
            @PathVariable UUID album, @PathVariable UUID photo, @RequestBody SharedAlbumService.SavePhoto request) {
        service.addPhoto(actor.userId(), space, album, photo, request); return ResponseEntity.ok().build();
    }
    @DeleteMapping("/{album}") public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID space, @PathVariable UUID album) {
        service.delete(actor.userId(), space, album); return ResponseEntity.noContent().build();
    }
    @PutMapping("/{album}/cover") public ResponseEntity<Void> cover(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID space, @PathVariable UUID album, @RequestBody SharedAlbumService.SaveCover request) {
        service.setCover(actor.userId(), space, album, request); return ResponseEntity.ok().build();
    }
    @DeleteMapping("/{album}/photos/{photo}") public ResponseEntity<Void> deletePhoto(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID space,
            @PathVariable UUID album, @PathVariable UUID photo) {
        service.deletePhoto(actor.userId(), space, album, photo); return ResponseEntity.noContent().build();
    }
}
