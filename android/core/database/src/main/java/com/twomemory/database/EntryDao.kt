package com.twomemory.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEntry(entry: EntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBlocks(blocks: List<EntryBlockEntity>)

    @Query("SELECT * FROM entries WHERE id = :entryId LIMIT 1")
    abstract suspend fun findEntry(entryId: String): EntryEntity?

    @Query("SELECT * FROM entry_blocks WHERE entryId = :entryId AND deleted = 0 ORDER BY orderKey, id")
    abstract suspend fun blocks(entryId: String): List<EntryBlockEntity>

    /** One record and its blocks as streams, so an open page follows later writes. */
    @Query("SELECT * FROM entries WHERE id = :entryId LIMIT 1")
    abstract fun observeEntry(entryId: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entry_blocks WHERE entryId = :entryId AND deleted = 0 ORDER BY orderKey, id")
    abstract fun observeBlocks(entryId: String): Flow<List<EntryBlockEntity>>

    /**
     * Live timeline stream: re-emits on every local write or pulled change.
     * Deliberately unbounded — a couple's whole archive is the list, and a cap
     * here would make their oldest records quietly disappear.
     */
    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY occurredAtEpochMillis DESC, id DESC")
    abstract fun observeTimeline(): Flow<List<EntryEntity>>

    /** All live blocks, so a timeline emission can derive previews without a query per entry. */
    @Query("SELECT * FROM entry_blocks WHERE deleted = 0 ORDER BY entryId, orderKey, id")
    abstract fun observeBlocks(): Flow<List<EntryBlockEntity>>

    @Query(
        "SELECT * FROM entry_blocks WHERE type = 'IMAGE' AND assetId IS NULL AND deleted = 0",
    )
    abstract suspend fun imageBlocksWithoutAsset(): List<EntryBlockEntity>

    /**
     * Published records only: the weekly re-encounter must never surface a draft
     * that was written but deliberately not shared.
     */
    @Query(
        "SELECT * FROM entries WHERE deleted = 0 AND state = 'PUBLISHED' " +
            "ORDER BY occurredAtEpochMillis DESC, id DESC",
    )
    abstract suspend fun publishedEntries(): List<EntryEntity>

    /**
     * The record from the *other* member that landed on this device last. Ordered
     * by rowid, not by occurredAt: writing about last weekend is new content with
     * an older date, and the notice is about what just arrived. One row is enough —
     * the notice says only that TA wrote something, so nothing else is read here.
     */
    @Query(
        "SELECT * FROM entries WHERE deleted = 0 AND state = 'PUBLISHED' AND authorId != :notAuthorId " +
            "ORDER BY rowid DESC LIMIT 1",
    )
    abstract suspend fun latestArrivedPublishedByOther(notAuthorId: String): EntryEntity?

    @Query("SELECT * FROM entry_blocks WHERE id = :blockId LIMIT 1")
    abstract suspend fun findBlock(blockId: String): EntryBlockEntity?

    @Query(
        "SELECT COUNT(*) FROM entry_blocks WHERE entryId = :entryId AND type = 'IMAGE' " +
            "AND assetId IS NULL AND deleted = 0",
    )
    abstract suspend fun imagesWithoutAsset(entryId: String): Int
}
