package com.twomemory.app.couple;

import java.util.List;
import java.util.UUID;

enum SpaceStatus {
    ACTIVE, UNPAIRED, CLOSED
}

enum ThemeKind {
    WARM_BEIGE, PURE_WHITE
}

record PairRequest(String oneTimeCode) {
}

record UpdateProfileRequest(String displayName, UUID avatarAssetId, String theme) {
}

record ProfileView(UUID userId, String displayName, UUID avatarAssetId, ThemeKind theme) {
}

record MemberView(UUID userId, ProfileView profile) {
}

record CoupleView(UUID id, SpaceStatus status, List<MemberView> members) {
}

record CreateSpaceResult(CoupleView couple, String pairingCode) {
}

record PairResult(CoupleView couple) {
}
