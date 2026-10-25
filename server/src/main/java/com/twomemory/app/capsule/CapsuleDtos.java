package com.twomemory.app.capsule;

import java.time.Instant;

enum CapsuleVisibility { SHARED, PRIVATE }

record SaveCapsuleRequest(String title, String body, String unlockDate, String visibility) { }

/** Body is null for a partner's sealed envelope; PRIVATE letters never enter their snapshot. */
record CapsuleView(String id, String coupleId, String authorId, String title, String body,
                   String unlockDate, CapsuleVisibility visibility, boolean unlocked, Instant updatedAt) { }
