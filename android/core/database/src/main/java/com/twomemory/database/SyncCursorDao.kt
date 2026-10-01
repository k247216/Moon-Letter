package com.twomemory.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncCursorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("SELECT nextSequence FROM sync_cursors WHERE coupleId = :coupleId LIMIT 1")
    suspend fun nextSequence(coupleId: String): Long?
}
