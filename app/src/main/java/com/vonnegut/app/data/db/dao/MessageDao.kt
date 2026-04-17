package com.vonnegut.app.data.db.dao

import androidx.room.*
import com.vonnegut.app.data.db.entities.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstMessage(sessionId: Long): Message?

    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId")
    suspend fun getMessageCount(sessionId: Long): Int

    @Insert
    suspend fun insertMessage(message: Message): Long

    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteAllMessagesForSession(sessionId: Long)

    /**
     * Prune oldest messages in a session, keeping only the most recent [keepCount].
     */
    @Query("""
        DELETE FROM messages
        WHERE session_id = :sessionId
          AND id NOT IN (
            SELECT id FROM messages
            WHERE session_id = :sessionId
            ORDER BY timestamp DESC
            LIMIT :keepCount
          )
    """)
    suspend fun pruneMessages(sessionId: Long, keepCount: Int)
}
