package com.twomemory.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AlbumDao {
    @Query("SELECT * FROM album_categories ORDER BY createdAtEpochMillis, id")
    abstract fun observeCategories(): Flow<List<AlbumCategoryEntity>>

    @Transaction
    @Query("SELECT * FROM independent_albums WHERE deleted = 0 ORDER BY createdAtEpochMillis DESC, id")
    abstract fun observeAlbums(): Flow<List<AlbumWithPhotos>>

    @Transaction
    @Query("SELECT * FROM independent_albums ORDER BY createdAtEpochMillis DESC, id")
    abstract suspend fun allAlbums(): List<AlbumWithPhotos>

    @Transaction
    open suspend fun claimUnbound(server: String, space: String, owner: String) {
        bindAlbums(server, space, owner)
        bindPhotos(server, space, owner)
    }

    @Query("UPDATE independent_albums SET serverUrl = :server, coupleId = :space, createdBy = :owner WHERE serverUrl IS NULL AND coupleId IS NULL")
    protected abstract suspend fun bindAlbums(server: String, space: String, owner: String)

    @Query("UPDATE album_photos SET addedBy=:owner WHERE addedBy IS NULL AND albumId IN " +
        "(SELECT id FROM independent_albums WHERE serverUrl=:server AND coupleId=:space AND createdBy=:owner)")
    protected abstract suspend fun bindPhotos(server: String, space: String, owner: String)

    @Query("SELECT * FROM album_photos WHERE id = :id")
    abstract suspend fun findPhoto(id: String): AlbumPhotoEntity?

    @Upsert abstract suspend fun upsertAlbum(album: AlbumEntity)
    @Upsert abstract suspend fun upsertPhoto(photo: AlbumPhotoEntity)

    @Query("UPDATE independent_albums SET cloudKnown=1 WHERE id=:id")
    abstract suspend fun acknowledgeAlbum(id: String)
    @Query("UPDATE album_photos SET assetId=:asset, addedBy=:owner WHERE id=:id")
    abstract suspend fun attachPhotoAsset(id: String, asset: String, owner: String)
    @Query("UPDATE album_photos SET cloudKnown=1 WHERE id=:id")
    abstract suspend fun acknowledgePhoto(id: String)
    @Query("UPDATE independent_albums SET coverDirty=0 WHERE id=:id AND coverPhotoId=:photo")
    abstract suspend fun acknowledgeCover(id: String, photo: String)
    @Query("UPDATE independent_albums SET deleted=1 WHERE id=:id")
    abstract suspend fun deleteAlbum(id: String)
    @Query("UPDATE album_photos SET deleted=1 WHERE id=:id")
    abstract suspend fun deletePhoto(id: String)

    @Query("SELECT * FROM independent_albums WHERE id = :id")
    abstract suspend fun findAlbum(id: String): AlbumEntity?

    @Query("SELECT * FROM album_categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findCategory(name: String): AlbumCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCategory(category: AlbumCategoryEntity): Long

    @Insert
    abstract suspend fun insertAlbum(album: AlbumEntity)

    @Insert
    abstract suspend fun insertPhoto(photo: AlbumPhotoEntity)

    @Query("UPDATE independent_albums SET coverPhotoId = :photoId, coverDirty=1 WHERE id = :albumId AND deleted=0 " +
        "AND EXISTS (SELECT 1 FROM album_photos WHERE id = :photoId AND albumId = :albumId AND deleted=0)")
    abstract suspend fun setCover(albumId: String, photoId: String): Int

    @Transaction
    open suspend fun addPhoto(photo: AlbumPhotoEntity) {
        insertPhoto(photo)
        if (findAlbum(photo.albumId)?.coverPhotoId == null) setCover(photo.albumId, photo.id)
    }
}
