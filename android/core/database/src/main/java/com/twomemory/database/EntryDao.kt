package com.twomemory.database

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryState
import com.twomemory.model.TimelineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

@Dao
abstract class EntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEntry(entry: EntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBlocks(blocks: List<EntryBlockEntity>)

    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY occurredAtEpochMillis DESC, id DESC")
    protected abstract fun timelineSource(): PagingSource<Int, EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :entryId LIMIT 1")
    abstract suspend fun findEntry(entryId: String): EntryEntity?

    @Query("SELECT * FROM entry_blocks WHERE entryId = :entryId AND deleted = 0 ORDER BY orderKey, id")
    abstract suspend fun blocks(entryId: String): List<EntryBlockEntity>

    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY occurredAtEpochMillis DESC, id DESC LIMIT 100")
    abstract suspend fun timelineSnapshot(): List<EntryEntity>

    /** Live timeline stream: re-emits on every local write or pulled change. */
    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY occurredAtEpochMillis DESC, id DESC LIMIT 100")
    abstract fun observeTimelineSnapshot(): Flow<List<EntryEntity>>

    @Query(
        "SELECT * FROM entry_blocks WHERE type = 'IMAGE' AND assetId IS NULL AND deleted = 0",
    )
    abstract suspend fun imageBlocksWithoutAsset(): List<EntryBlockEntity>

    @Query("SELECT * FROM entry_blocks WHERE id = :blockId LIMIT 1")
    abstract suspend fun findBlock(blockId: String): EntryBlockEntity?

    @Query(
        "SELECT COUNT(*) FROM entry_blocks WHERE entryId = :entryId AND type = 'IMAGE' " +
            "AND assetId IS NULL AND deleted = 0",
    )
    abstract suspend fun imagesWithoutAsset(entryId: String): Int

    fun observeTimeline(): Flow<PagingData<TimelineItem>> = Pager(
        config = PagingConfig(pageSize = 30, enablePlaceholders = false),
        pagingSourceFactory = ::timelineSource,
    ).flow.map { paging ->
        paging.map { entity ->
            TimelineItem(
                id = UUID.fromString(entity.id),
                coupleId = UUID.fromString(entity.coupleId),
                mode = EntryMode.valueOf(entity.mode),
                state = EntryState.valueOf(entity.state),
                occurredAt = Instant.ofEpochMilli(entity.occurredAtEpochMillis),
                occurredTimezone = entity.occurredTimezone,
                title = entity.title,
            )
        }
    }
}
