package com.twomemory.app.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 6 acceptance: per-couple sequences are allocated under a lock on the
 * couple's sync state row inside the mutation transaction. While transaction
 * A holds its lock, transaction B cannot commit a higher sequence for the
 * same couple; after A commits, B proceeds with the next sequence — so a
 * paging client advancing the cursor cannot skip a committed change.
 */
@SpringBootTest
class ChangeFeedOrderingTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_order_test";

    @Autowired
    private ChangeFeedService changeFeedService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
        registry.add("spring.datasource.username", () -> ADMIN_USER);
        registry.add("spring.datasource.password", () -> ADMIN_PASSWORD);
    }

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated ordering test database", exception);
        }
    }

    @Test
    void concurrentAllocationSerializesOnCommitOrder() throws Exception {
        UUID coupleId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO couple_space (id, status) VALUES (?, 'ACTIVE')", coupleId);

        // Transaction A on this thread: allocates sequence 1 and holds the
        // state row lock (transaction open, not yet committed).
        TransactionStatus statusA = transactionManager.getTransaction(new DefaultTransactionDefinition());
        AtomicLong seqA = new AtomicLong(-1);
        AtomicReference<Exception> failureA = new AtomicReference<>();
        try {
            seqA.set(changeFeedService.appendChange(coupleId, "ENTRY",
                    UUID.randomUUID(), "CREATE", "{\"side\":\"A\"}"));
        } catch (Exception exception) {
            failureA.set(exception);
        }
        assertThat(failureA.get()).isNull();
        assertThat(seqA.get()).isEqualTo(1);

        // Transaction B on a worker thread: must block on the state row lock
        // until A commits — even though B is fully free to run otherwise.
        AtomicLong seqB = new AtomicLong(-1);
        AtomicReference<Exception> failureB = new AtomicReference<>();
        CountDownLatch bDone = new CountDownLatch(1);
        Thread threadB = new Thread(() -> {
            try {
                seqB.set(changeFeedService.appendChange(coupleId, "ENTRY",
                        UUID.randomUUID(), "CREATE", "{\"side\":\"B\"}"));
            } catch (Exception exception) {
                failureB.set(exception);
            } finally {
                bDone.countDown();
            }
        });
        threadB.start();
        boolean finishedEarly = bDone.await(400, TimeUnit.MILLISECONDS);
        assertThat(finishedEarly)
                .as("B must not be able to allocate while A holds the state row lock")
                .isFalse();
        assertThat(threadB.isAlive()).isTrue();

        // Commit A; B then acquires the lock and allocates the next sequence.
        transactionManager.commit(statusA);
        assertThat(bDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failureB.get()).isNull();
        assertThat(seqB.get()).isEqualTo(2);

        // A client paging after cursor 0 sees A's change first with an
        // explicit has_more, so B's committed change cannot be skipped.
        ChangePage page1 = changeFeedService.readChanges(coupleId, 0, 1);
        assertThat(page1.changes()).hasSize(1);
        assertThat(page1.changes().get(0).sequence()).isEqualTo(seqA.get());
        assertThat(page1.hasMore()).isTrue();
        assertThat(page1.nextSequence()).isEqualTo(seqA.get());
        ChangePage page2 = changeFeedService.readChanges(coupleId, page1.nextSequence(), 1);
        assertThat(page2.changes().get(0).sequence()).isEqualTo(seqB.get());
        assertThat(page2.hasMore()).isFalse();
        assertThat(page2.nextSequence()).isEqualTo(seqB.get());
    }
}
