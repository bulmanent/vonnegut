package com.vonnegut.app.data.db.dao

import androidx.room.*
import com.vonnegut.app.data.db.entities.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions WHERE id IN (SELECT DISTINCT session_id FROM messages) ORDER BY updated_at DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions ORDER BY updated_at DESC LIMIT 1")
    suspend fun getMostRecentSession(): Session?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): Session?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    @Update
    suspend fun updateSession(session: Session)

    @Query("UPDATE sessions SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun renameSession(id: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touchSession(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET prune_limit = :limit WHERE id = :id")
    suspend fun setPruneLimit(id: Long, limit: Int)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}
