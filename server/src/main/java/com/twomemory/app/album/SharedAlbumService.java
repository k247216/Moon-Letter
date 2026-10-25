package com.twomemory.app.album;

import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SharedAlbumService {
    private final JdbcTemplate db;
    private final SpaceAccessPolicy access;
    public SharedAlbumService(JdbcTemplate db, SpaceAccessPolicy access) {
        this.db = db; this.access = access;
    }
    public record SaveAlbum(String name, String category) {}
    public record SavePhoto(UUID assetId, long takenAtEpochMillis) {}
    public record SaveCover(UUID assetId) {}
    public record Photo(UUID id, UUID assetId, UUID addedBy, long takenAtEpochMillis,
                        String mimeType, Integer width, Integer height, boolean deleted) {}
    public record Album(UUID id, String name, String category, UUID createdBy,
                        long createdAtEpochMillis, UUID coverAssetId, boolean deleted, List<Photo> photos) {}
    private record Identity(UUID space, UUID owner, boolean deleted) {}

    /** A complete scoped snapshot includes tombstones so an offline phone cannot resurrect deletions. */
    @Transactional(readOnly = true)
    public List<Album> read(UUID actor, UUID space) {
        access.requireMember(actor, space);
        Map<UUID, List<Photo>> photosByAlbum = new HashMap<>();
        db.query("""
            SELECT i.*, m.mime_type,m.width,m.height FROM album_item i
            JOIN album a ON a.id=i.album_id
            JOIN media_asset m ON m.id=i.media_asset_id
            WHERE a.couple_id=? ORDER BY i.album_id,i.order_key
            """, (org.springframework.jdbc.core.RowCallbackHandler) p -> {
                Photo photo = new Photo(p.getObject("photo_id", UUID.class),
                    p.getObject("media_asset_id", UUID.class), p.getObject("added_by", UUID.class),
                    p.getLong("taken_at_epoch_millis"), p.getString("mime_type"),
                    p.getObject("width", Integer.class), p.getObject("height", Integer.class),
                    p.getTimestamp("deleted_at") != null);
                photosByAlbum.computeIfAbsent(p.getObject("album_id", UUID.class), key -> new ArrayList<>())
                    .add(photo);
            }, space);
        return db.query("SELECT * FROM album WHERE couple_id=? ORDER BY created_at DESC,id", (rs, n) -> {
            UUID id = rs.getObject("id", UUID.class);
            List<Photo> photos = photosByAlbum.getOrDefault(id, List.of());
            return new Album(id, rs.getString("title"), rs.getString("category"),
                rs.getObject("created_by", UUID.class), rs.getTimestamp("created_at").getTime(),
                rs.getObject("cover_asset_id", UUID.class), rs.getTimestamp("deleted_at") != null, photos);
        }, space);
    }
    @Transactional
    public void save(UUID actor, UUID space, UUID album, SaveAlbum request) {
        lockSpace(actor, space);
        String name = text(request == null ? null : request.name(), 80);
        String category = text(request == null ? null : request.category(), 40);
        var existing = identity(album);
        if (existing == null) {
            db.update("INSERT INTO album(id,couple_id,title,category,created_by) VALUES (?,?,?,?,?)",
                album, space, name, category, actor);
        } else {
            check(existing, actor, space, true);
            db.update("UPDATE album SET title=?,category=?,updated_at=now() WHERE id=?", name, category, album);
        }
    }
    @Transactional
    public void addPhoto(UUID actor, UUID space, UUID album, UUID photo, SavePhoto request) {
        lockSpace(actor, space);
        check(identity(album), actor, space, false);
        if (request == null || request.assetId() == null || request.takenAtEpochMillis() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo metadata required");
        var assetOwners = db.query("""
            SELECT owner_id FROM media_asset WHERE id=? AND couple_id=? AND status='READY'
            AND kind='IMAGE' AND deleted_at IS NULL
            """, (rs,n) -> rs.getObject(1, UUID.class), request.assetId(), space);
        if (assetOwners.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "photo must be a verified image in this space");
        var previous = db.query("SELECT album_id,media_asset_id,added_by,deleted_at FROM album_item WHERE photo_id=?",
            (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getTimestamp(4)}, photo);
        if (!previous.isEmpty()) {
            Object[] row = previous.getFirst();
            if (!album.equals(row[0]) || !request.assetId().equals(row[1]) || !actor.equals(row[2]))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "photo id already used");
            if (row[3] != null) throw new ResponseStatusException(HttpStatus.GONE, "photo was deleted");
            return;
        }
        if (!actor.equals(assetOwners.getFirst()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only uploader may add this photo");
        db.update("""
            INSERT INTO album_item(album_id,media_asset_id,order_key,added_by,photo_id,taken_at_epoch_millis)
            VALUES (?, ?, (SELECT COALESCE(MAX(order_key),0)+1 FROM album_item WHERE album_id=?), ?, ?, ?)
            """, album, request.assetId(), album, actor, photo, request.takenAtEpochMillis());
        db.update("UPDATE album SET cover_asset_id=COALESCE(cover_asset_id,?),updated_at=now() WHERE id=?",
            request.assetId(), album);
    }
    @Transactional
    public void setCover(UUID actor, UUID space, UUID album, SaveCover request) {
        lockSpace(actor, space);
        check(identity(album), actor, space, false);
        if (request == null || request.assetId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cover photo required");
        Integer exists = db.queryForObject("""
            SELECT count(*) FROM album_item WHERE album_id=? AND media_asset_id=? AND deleted_at IS NULL
            """, Integer.class, album, request.assetId());
        if (exists == null || exists != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cover must be an active album photo");
        db.update("UPDATE album SET cover_asset_id=?,updated_at=now() WHERE id=?", request.assetId(), album);
    }
    @Transactional
    public void delete(UUID actor, UUID space, UUID album) {
        lockSpace(actor, space);
        Identity identity = identity(album);
        if (identity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!space.equals(identity.space()) || !actor.equals(identity.owner()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        db.update("UPDATE album SET deleted_at=COALESCE(deleted_at,now()),updated_at=now() WHERE id=?", album);
    }
    @Transactional
    public void deletePhoto(UUID actor, UUID space, UUID album, UUID photo) {
        lockSpace(actor, space);
        check(identity(album), actor, space, false);
        UUID addedBy = db.query("SELECT added_by FROM album_item WHERE album_id=? AND photo_id=?",
            (rs,n) -> rs.getObject(1, UUID.class), album, photo).stream().findFirst().orElse(null);
        if (addedBy == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!actor.equals(addedBy)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        db.update("UPDATE album_item SET deleted_at=COALESCE(deleted_at,now()) WHERE album_id=? AND photo_id=?", album, photo);
        db.update("""
            UPDATE album SET cover_asset_id=CASE
            WHEN EXISTS (SELECT 1 FROM album_item WHERE album_id=?
                AND media_asset_id=album.cover_asset_id AND deleted_at IS NULL) THEN cover_asset_id
            ELSE (SELECT media_asset_id FROM album_item
                WHERE album_id=? AND deleted_at IS NULL ORDER BY order_key LIMIT 1) END,
            updated_at=now() WHERE id=?
            """, album, album, album);
    }
    private void lockSpace(UUID actor, UUID space) {
        access.requireMember(actor, space);
        db.queryForObject("SELECT id FROM couple_space WHERE id=? FOR UPDATE", UUID.class, space);
    }
    private Identity identity(UUID album) {
        return db.query("SELECT couple_id,created_by,deleted_at FROM album WHERE id=?",
            (rs,n) -> new Identity(rs.getObject(1, UUID.class),rs.getObject(2, UUID.class),rs.getTimestamp(3)!=null),
            album).stream().findFirst().orElse(null);
    }
    private static void check(Identity identity, UUID actor, UUID space, boolean ownerOnly) {
        if (identity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!space.equals(identity.space()) || (ownerOnly && !actor.equals(identity.owner())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (identity.deleted()) throw new ResponseStatusException(HttpStatus.GONE, "album was deleted");
    }
    private static String text(String raw, int limit) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.codePointCount(0,value.length()) > limit)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid album name or category");
        return value;
    }
}
