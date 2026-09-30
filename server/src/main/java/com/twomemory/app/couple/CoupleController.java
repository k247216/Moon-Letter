package com.twomemory.app.couple;

import com.twomemory.app.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/couple")
public class CoupleController {

    private final CoupleService coupleService;

    public CoupleController(CoupleService coupleService) {
        this.coupleService = coupleService;
    }

    @PostMapping
    public ResponseEntity<CreateSpaceResult> createSpace(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId) {
        UUID userId = AuthenticatedUser.fromHeader(rawUserId).userId();
        return ResponseEntity.status(HttpStatus.CREATED).body(coupleService.createSpace(userId));
    }

    @PostMapping("/pair")
    public PairResult pair(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestBody PairRequest request) {
        UUID userId = AuthenticatedUser.fromHeader(rawUserId).userId();
        return coupleService.pair(userId, request.oneTimeCode());
    }

    @GetMapping("/{coupleId}")
    public CoupleView getSpace(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @PathVariable UUID coupleId) {
        UUID userId = AuthenticatedUser.fromHeader(rawUserId).userId();
        return coupleService.readSpace(userId, coupleId);
    }

    @PatchMapping("/{coupleId}/members/{targetUserId}/profile")
    public ProfileView updateProfile(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @PathVariable UUID coupleId,
            @PathVariable UUID targetUserId,
            @RequestBody UpdateProfileRequest request) {
        UUID actorId = AuthenticatedUser.fromHeader(rawUserId).userId();
        return coupleService.updateProfile(actorId, coupleId, targetUserId, request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("COUPLE_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> badRequest(ValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", exception.getMessage()));
    }

    record ApiError(String code, String message) {
    }
}
