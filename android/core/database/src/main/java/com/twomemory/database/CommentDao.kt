package com.twomemory.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: CommentEntity)

    @Query("SELECT * FROM comments WHERE entryId = :entryId AND deleted = 0 ORDER BY createdAtEpochMillis, id")
    suspend fun commentsForEntry(entryId: String): List<CommentEntity>

    @Query("SELECT * FROM comments WHERE id = :commentId LIMIT 1")
    suspend fun find(commentId: String): CommentEntity?

    @Query("UPDATE comments SET deleted = 1 WHERE id = :commentId")
    suspend fun markDeleted(commentId: String)
}
