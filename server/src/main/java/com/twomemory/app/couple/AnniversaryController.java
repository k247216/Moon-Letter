package com.twomemory.app.couple;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/couple/{coupleId}/anniversary")
public class AnniversaryController {

    private final AnniversaryService anniversaryService;

    public AnniversaryController(AnniversaryService anniversaryService) {
        this.anniversaryService = anniversaryService;
    }

    @GetMapping
    public ResponseEntity<AnniversaryView> read(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID coupleId) {
        AnniversaryView view = anniversaryService.read(actor.userId(), coupleId);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    @PutMapping
    public AnniversaryView save(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID coupleId,
            @RequestBody SaveAnniversaryRequest request) {
        return anniversaryService.save(actor.userId(), coupleId, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<CoupleController.ApiError> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(403)
                .body(new CoupleController.ApiError("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<CoupleController.ApiError> badRequest(ValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new CoupleController.ApiError("VALIDATION_ERROR", exception.getMessage()));
    }
}
