package com.twomemory.app.occasion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

enum OccasionKind { BIRTHDAY, ANNIVERSARY, SURPRISE }

record SaveOccasionRequest(String kind, String title, String body, String date,
                           Boolean repeatsYearly, UUID recipientId, UUID photoAssetId, String visibility) {
    SaveOccasionRequest(String kind, String title, String body, String date,
                        Boolean repeatsYearly, UUID recipientId, UUID photoAssetId) {
        this(kind, title, body, date, repeatsYearly, recipientId, photoAssetId, null);
    }
}

record OccasionView(UUID id, UUID coupleId, UUID authorId, OccasionKind kind, String title,
                    String body, LocalDate date, boolean repeatsYearly, UUID recipientId,
                    UUID photoAssetId, Instant updatedAt, String visibility) {
    OccasionView(UUID id, UUID coupleId, UUID authorId, OccasionKind kind, String title,
                 String body, LocalDate date, boolean repeatsYearly, UUID recipientId,
                 UUID photoAssetId, Instant updatedAt) {
        this(id, coupleId, authorId, kind, title, body, date, repeatsYearly, recipientId,
                photoAssetId, updatedAt, "SHARED");
    }
}
