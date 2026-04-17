package com.vonnegut.app.data.repository

import com.vonnegut.app.data.db.dao.MessageDao
import com.vonnegut.app.data.db.dao.SessionDao
import com.vonnegut.app.data.db.entities.Message
import com.vonnegut.app.data.db.entities.Session
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {

    fun getAllSessions(): Flow<List<Session>> = sessionDao.getAllSessions()

    fun getMessagesForSession(sessionId: Long): Flow<List<Message>> =
        messageDao.getMessagesForSession(sessionId)

    suspend fun getMessagesSync(sessionId: Long): List<Message> =
        messageDao.getMessagesForSessionSync(sessionId)

    suspend fun getMostRecentSession(): Session? = sessionDao.getMostRecentSession()

    suspend fun getSessionById(id: Long): Session? = sessionDao.getSessionById(id)

    suspend fun createSession(name: String? = null): Session {
        val sessionName = name ?: defaultSessionName()
        val session = Session(name = sessionName)
        val id = sessionDao.insertSession(session)
        return session.copy(id = id)
    }

    suspend fun renameSession(sessionId: Long, name: String) {
        sessionDao.renameSession(sessionId, name)
    }

    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteSession(sessionId)
    }

    suspend fun setPruneLimit(sessionId: Long, limit: Int) {
        sessionDao.setPruneLimit(sessionId, limit)
    }

    suspend fun pruneSession(sessionId: Long, keepCount: Int) {
        messageDao.pruneMessages(sessionId, keepCount)
    }

    suspend fun clearSession(sessionId: Long) {
        messageDao.deleteAllMessagesForSession(sessionId)
    }

    suspend fun insertMessage(message: Message): Long {
        val id = messageDao.insertMessage(message)
        sessionDao.touchSession(message.sessionId)
        return id
    }

    suspend fun getMessageCount(sessionId: Long): Int =
        messageDao.getMessageCount(sessionId)

    suspend fun getFirstMessage(sessionId: Long): Message? =
        messageDao.getFirstMessage(sessionId)

    private fun defaultSessionName(): String {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }
}
