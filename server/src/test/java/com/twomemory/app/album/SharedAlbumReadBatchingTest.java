package com.twomemory.app.album;

import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.auth.SpaceAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real SQL and independent expected records; counts executions, not SQL text or elapsed time. */
@SpringBootTest
class SharedAlbumReadBatchingTest {
    private static final String TEST_DB = "moon_letter_album_read_batching_test";
    private static final long BASE_TIME = 1_700_000_000_000L;

    static {
        // Dedicated synthetic database, following the existing isolated regression harness.
        try (Connection connection = DriverManager.getConnection(TestDatabaseConfig.adminUrl(),
                TestDatabaseConfig.user(), TestDatabaseConfig.password());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception failure) {
            throw new IllegalStateException("could not prepare isolated album read test database", failure);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
    }

    @Autowired private JdbcTemplate fixtures;
    @Autowired private DataSource dataSource;
    private CountingDataSource counted;
    private SharedAlbumService service;
    private UUID space, otherSpace, owner, partner, outsider;

    @BeforeEach
    void setup() {
        assertThat(fixtures.queryForObject("SELECT current_database()", String.class)).isEqualTo(TEST_DB);
        space = UUID.randomUUID();
        otherSpace = UUID.randomUUID();
        owner = UUID.randomUUID();
        partner = UUID.randomUUID();
        outsider = UUID.randomUUID();
        for (UUID id : new UUID[]{space, otherSpace})
            fixtures.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", id);
        for (UUID id : new UUID[]{owner, partner, outsider})
            fixtures.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", id);
        for (UUID id : new UUID[]{owner, partner})
            fixtures.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", space, id);
        fixtures.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", otherSpace, outsider);
        counted = new CountingDataSource(dataSource);
        JdbcTemplate reads = new JdbcTemplate(counted);
        service = new SharedAlbumService(reads, new SpaceAccessPolicy(reads));
    }

    @Test
    void queryBudgetStaysConstantWhenAlbumCountGrowsFromOneToEight() {
        List<SharedAlbumService.Album> expected = new ArrayList<>();
        UUID firstId = UUID.randomUUID();
        SharedAlbumService.Photo firstPhoto = photo(firstId, space, owner, 10, false, false, 20, 30);
        expected.add(album(firstId, space, owner, BASE_TIME, false, firstPhoto.assetId(), List.of(firstPhoto)));

        counted.executions.set(0);
        assertThat(service.read(partner, space)).containsExactlyElementsOf(expected);
        assertThat(counted.executions.get()).as("one album: membership plus at most two snapshot queries")
                .isLessThanOrEqualTo(3);

        for (int index = 1; index < 8; index++) {
            UUID id = UUID.randomUUID();
            SharedAlbumService.Photo item = photo(id, space, partner, 10, false, false, 40, 50);
            expected.add(album(id, space, partner, BASE_TIME + index * 1000L, false, item.assetId(), List.of(item)));
        }
        expected.sort(albumOrder());
        counted.executions.set(0);
        assertThat(service.read(partner, space)).containsExactlyElementsOf(expected);
        assertThat(counted.executions.get()).as("eight albums must share a batched photo query, not one per album")
                .isLessThanOrEqualTo(3);
    }

    @Test
    void fullSnapshotPreservesFieldsOrderingEmptyAlbumsAndBothKindsOfTombstones() {
        UUID liveId = UUID.randomUUID(), emptyId = UUID.randomUUID(), deletedId = UUID.randomUUID();
        UUID tiedId = UUID.randomUUID();
        // Insert out of order; a deleted media row must not hide its album-item tombstone.
        SharedAlbumService.Photo last = photo(liveId, space, partner, 30, false, false, 800, 600);
        SharedAlbumService.Photo first = photo(liveId, space, owner, 10, true, true, null, null);
        SharedAlbumService.Photo middle = photo(liveId, space, owner, 20, false, false, 20, 30);
        SharedAlbumService.Photo deletedAlbumPhoto = photo(deletedId, space, partner, 10, true, false, 50, 60);
        // Same asset in another album catches accidental grouping by asset rather than album.
        UUID sharedPhotoId = UUID.randomUUID();
        ensureAlbum(tiedId, space, partner);
        fixtures.update("""
                INSERT INTO album_item(album_id,media_asset_id,order_key,added_by,photo_id,taken_at_epoch_millis)
                VALUES (?,?,10,?,?,77)
                """, tiedId, middle.assetId(), partner, sharedPhotoId);
        SharedAlbumService.Photo shared = new SharedAlbumService.Photo(sharedPhotoId, middle.assetId(),
                partner, 77, "image/png", 20, 30, false);
        List<SharedAlbumService.Album> expected = new ArrayList<>(List.of(
                album(liveId, space, owner, BASE_TIME + 2000, false, middle.assetId(), List.of(first, middle, last)),
                album(emptyId, space, partner, BASE_TIME + 1000, false, null, List.of()),
                album(deletedId, space, owner, BASE_TIME, true, null, List.of(deletedAlbumPhoto)),
                album(tiedId, space, partner, BASE_TIME + 2000, false, middle.assetId(), List.of(shared))));
        expected.sort(albumOrder());
        UUID foreignId = UUID.randomUUID();
        SharedAlbumService.Photo foreign = photo(foreignId, otherSpace, outsider, 10, false, false, 99, 99);
        album(foreignId, otherSpace, outsider, BASE_TIME + 9000, false, foreign.assetId(), List.of(foreign));

        assertThat(service.read(owner, space)).containsExactlyElementsOf(expected);
        assertThat(service.read(partner, space)).containsExactlyElementsOf(expected);
    }

    @Test
    void emptySpaceDoesNotReturnForeignAlbumsOrPhotos() {
        UUID foreignId = UUID.randomUUID();
        SharedAlbumService.Photo foreign = photo(foreignId, otherSpace, outsider, 10, false, false, 12, 13);
        SharedAlbumService.Album foreignAlbum = album(foreignId, otherSpace, outsider, BASE_TIME,
                false, foreign.assetId(), List.of(foreign));
        assertThat(service.read(owner, space)).isEmpty();
        assertThat(service.read(outsider, otherSpace)).containsExactly(foreignAlbum);
    }

    @Test
    void nonMemberIsRejectedBeforeAnyAlbumOrPhotoQuery() {
        UUID id = UUID.randomUUID();
        album(id, space, owner, BASE_TIME, false, null, List.of());
        counted.executions.set(0);
        assertThatThrownBy(() -> service.read(outsider, space))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(403));
        assertThat(counted.executions.get()).as("only the membership check may run").isEqualTo(1);
    }

    private SharedAlbumService.Album album(UUID id, UUID albumSpace, UUID creator, long created,
                                           boolean deleted, UUID cover, List<SharedAlbumService.Photo> photos) {
        // Photos are seeded first into a placeholder album to satisfy foreign keys.
        ensureAlbum(id, albumSpace, creator);
        String title = "album-" + id.toString().substring(0, 8);
        fixtures.update("""
                UPDATE album SET title=?,category='旅行',created_by=?,created_at=?,cover_asset_id=?,deleted_at=? WHERE id=?
                """, title, creator, new Timestamp(created), cover, deleted ? new Timestamp(BASE_TIME + 9000) : null, id);
        return new SharedAlbumService.Album(id, title, "旅行", creator, created, cover, deleted, photos);
    }

    private void ensureAlbum(UUID id, UUID albumSpace, UUID creator) {
        fixtures.update("""
                INSERT INTO album(id,couple_id,title,created_by) VALUES (?,?,'placeholder',?)
                ON CONFLICT (id) DO NOTHING
                """, id, albumSpace, creator);
    }

    private SharedAlbumService.Photo photo(UUID albumId, UUID albumSpace, UUID addedBy, long order,
                                           boolean deleted, boolean mediaDeleted, Integer width, Integer height) {
        ensureAlbum(albumId, albumSpace, addedBy);
        UUID assetId = UUID.randomUUID(), photoId = UUID.randomUUID();
        fixtures.update("""
                INSERT INTO media_asset(id,couple_id,owner_id,kind,status,object_key,mime_type,byte_size,sha256,width,height,deleted_at)
                VALUES (?,?,?,'IMAGE','READY',?,'image/png',10,?,?,?,?)
                """, assetId, albumSpace, addedBy, "synthetic/album-batch/" + assetId, "a".repeat(64),
                width, height, mediaDeleted ? new Timestamp(BASE_TIME + 9000) : null);
        fixtures.update("""
                INSERT INTO album_item(album_id,media_asset_id,order_key,added_by,photo_id,taken_at_epoch_millis,deleted_at)
                VALUES (?,?,?,?,?,?,?)
                """, albumId, assetId, order, addedBy, photoId, BASE_TIME + order,
                deleted ? new Timestamp(BASE_TIME + 9000) : null);
        return new SharedAlbumService.Photo(photoId, assetId, addedBy, BASE_TIME + order,
                "image/png", width, height, deleted);
    }

    private static Comparator<SharedAlbumService.Album> albumOrder() {
        return Comparator.comparingLong(SharedAlbumService.Album::createdAtEpochMillis).reversed()
                .thenComparing(album -> album.id().toString());
    }

    /** Passes through to PostgreSQL unchanged; counts real JDBC execution calls on the read path. */
    private static final class CountingDataSource extends DelegatingDataSource {
        private final AtomicInteger executions = new AtomicInteger();

        private CountingDataSource(DataSource target) { super(target); }

        @Override public Connection getConnection() throws java.sql.SQLException {
            return wrap(super.getConnection());
        }

        @Override public Connection getConnection(String username, String password) throws java.sql.SQLException {
            return wrap(super.getConnection(username, password));
        }

        private Connection wrap(Connection target) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(target, arguments);
                            if (result instanceof Statement statement) {
                                Class<?> type = statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                                return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                                        (statementProxy, statementMethod, statementArguments) -> {
                                            if (statementMethod.getName().startsWith("execute")) executions.incrementAndGet();
                                            try {
                                                return statementMethod.invoke(statement, statementArguments);
                                            } catch (InvocationTargetException failure) {
                                                throw failure.getCause();
                                            }
                                        });
                            }
                            return result;
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }
    }
}
