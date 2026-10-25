package com.twomemory.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CommentNoticeTarget(val id: String, val entryId: String)

@Dao
interface CommentDao {
    @Query("SELECT c.id AS id, c.entryId AS entryId FROM comments c INNER JOIN entries e ON e.id = c.entryId " +
        "WHERE e.coupleId = :coupleId AND c.authorId != :ownId AND c.deleted = 0 AND e.deleted = 0 " +
        "AND e.state = 'PUBLISHED' AND (e.visibility = 'SHARED' OR e.authorId = :ownId) ORDER BY c.rowid")
    suspend fun noticeTargets(coupleId: String, ownId: String): List<CommentNoticeTarget>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: CommentEntity)

    @Query("SELECT * FROM comments WHERE entryId = :entryId AND deleted = 0 ORDER BY createdAtEpochMillis, id")
    suspend fun commentsForEntry(entryId: String): List<CommentEntity>

    @Query("SELECT * FROM comments WHERE entryId = :entryId AND deleted = 0 ORDER BY createdAtEpochMillis, id")
    fun observeForEntry(entryId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE id = :commentId LIMIT 1")
    suspend fun find(commentId: String): CommentEntity?

    @Query("UPDATE comments SET deleted = 1 WHERE id = :commentId")
    suspend fun markDeleted(commentId: String)
}
