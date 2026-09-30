package com.twomemory.app.sync;

import java.util.UUID;

public interface SyncNotificationPublisher {
    void notifySpaceChanged(UUID coupleId, long latestSequence);
}
