package com.twomemory.app.sync;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FcmSyncNotificationPublisher implements SyncNotificationPublisher {

    @Override
    public void notifySpaceChanged(UUID coupleId, long latestSequence) {
        // FCM credentials and device tokens are intentionally deferred. The payload contract
        // is kept narrow here so the eventual adapter cannot include diary content.
    }
}
