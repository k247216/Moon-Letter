package com.twomemory.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Independent album data never creates diary entries. */
data class AlbumScope(val serverUrl: String, val coupleId: String, val userId: String)
@Entity(tableName = "album_categories", indices = [Index(value = ["name"], unique = true)])
data class AlbumCategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "independent_albums",
    foreignKeys = [ForeignKey(
        entity = AlbumCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("categoryId")],
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String,
    val createdAtEpochMillis: Long,
    // Validated by AlbumDao.setCover; nullable until the first successful import.
    val coverPhotoId: String? = null,
    val serverUrl: String? = null,
    val coupleId: String? = null,
    val createdBy: String? = null,
    @ColumnInfo(defaultValue = "0") val cloudKnown: Boolean = false,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val coverDirty: Boolean = false,
)

@Entity(
    tableName = "album_photos",
    foreignKeys = [ForeignKey(
        entity = AlbumEntity::class, parentColumns = ["id"], childColumns = ["albumId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("albumId"), Index(value = ["localPath"], unique = true)],
)
data class AlbumPhotoEntity(
    @PrimaryKey val id: String,
    val albumId: String,
    val localPath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val takenAtEpochMillis: Long,
    val importedAtEpochMillis: Long,
    val assetId: String? = null,
    val addedBy: String? = null,
    @ColumnInfo(defaultValue = "0") val cloudKnown: Boolean = false,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)

data class AlbumWithPhotos(
    @Embedded val album: AlbumEntity,
    @Relation(parentColumn = "id", entityColumn = "albumId") val photos: List<AlbumPhotoEntity>,
) {
    val orderedPhotos: List<AlbumPhotoEntity>
        get() = photos.filterNot { it.deleted }.sortedWith(compareByDescending<AlbumPhotoEntity> { it.takenAtEpochMillis }.thenBy { it.id })
    val cover: AlbumPhotoEntity?
        get() = photos.firstOrNull { !it.deleted && it.id == album.coverPhotoId } ?: orderedPhotos.firstOrNull()
}
