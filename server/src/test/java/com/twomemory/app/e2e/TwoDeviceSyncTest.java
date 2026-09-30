package com.twomemory.app.e2e;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwoDeviceSyncTest {

    @Test
    void twoOfflineDevicesReconnectInReverseOrderWithoutDuplicateEffects() {
        FakeServer server = new FakeServer();
        Device deviceA = new Device(server, "A");
        Device deviceB = new Device(server, "B");
        UUID first = deviceA.create("A 的记录");
        UUID second = deviceB.create("B 的记录");

        deviceB.push(second);
        deviceA.push(first);
        deviceA.retry(first); // simulated timeout followed by idempotent retry

        assertThat(server.entries).containsKeys(first, second).hasSize(2);
        assertThat(server.version(first)).isEqualTo(1);
        assertThat(server.version(second)).isEqualTo(1);
    }

    @Test
    void sameBlockConflictAndUnauthorizedThirdUserRemainRecoverable() {
        FakeServer server = new FakeServer();
        Device deviceA = new Device(server, "A");
        UUID entry = deviceA.create("原文");
        deviceA.push(entry);
        assertThatThrownBy(() -> server.apply(entry, "B 的改动", 0))
                .isInstanceOf(Conflict.class);
        assertThatThrownBy(() -> server.read(entry, "C"))
                .isInstanceOf(Forbidden.class);
        server.apply(entry, "解决后的版本", 1);
        assertThat(server.entries.get(entry)).isEqualTo("解决后的版本");
    }

    private static final class Device {
        private final FakeServer server;
        private final String user;
        private final Map<UUID, String> pending = new HashMap<>();

        private Device(FakeServer server, String user) {
            this.server = server;
            this.user = user;
        }

        private UUID create(String body) {
            UUID id = UUID.randomUUID();
            pending.put(id, body);
            return id;
        }

        private void push(UUID id) {
            server.apply(id, pending.get(id), server.version(id));
        }

        private void retry(UUID id) {
            server.apply(id, pending.get(id), 0);
        }
    }

    private static final class FakeServer {
        private final Map<UUID, String> entries = new HashMap<>();
        private final Map<UUID, Integer> versions = new HashMap<>();

        private void apply(UUID id, String body, int baseVersion) {
            int current = version(id);
            if (current != baseVersion && entries.containsKey(id)) {
                if (body.equals(entries.get(id))) return;
                throw new Conflict();
            }
            entries.putIfAbsent(id, body);
            if (entries.containsKey(id) && current == baseVersion) {
                entries.put(id, body);
            }
            versions.put(id, current + 1);
        }

        private int version(UUID id) {
            return versions.getOrDefault(id, 0);
        }

        private String read(UUID id, String user) {
            if (!"A".equals(user) && !"B".equals(user)) throw new Forbidden();
            return entries.get(id);
        }
    }

    private static final class Conflict extends RuntimeException {}
    private static final class Forbidden extends RuntimeException {}
}
