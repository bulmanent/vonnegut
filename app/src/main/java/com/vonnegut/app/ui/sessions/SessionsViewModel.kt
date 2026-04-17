package com.vonnegut.app.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vonnegut.app.VonnegutApplication
import com.vonnegut.app.data.db.entities.Session
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VonnegutApplication
    private val repository = app.chatRepository

    val sessions: StateFlow<List<SessionUi>> = repository.getAllSessions()
        .map { sessions ->
            sessions.map { session ->
                val count = repository.getMessageCount(session.id)
                val first = repository.getFirstMessage(session.id)?.content ?: ""
                SessionUi(session, count, first.take(80))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            repository.deleteSession(session.id)
        }
    }

    fun pruneSession(session: Session, keepCount: Int) {
        viewModelScope.launch {
            repository.pruneSession(session.id, keepCount)
            repository.setPruneLimit(session.id, keepCount)
        }
    }

    fun clearSession(session: Session) {
        viewModelScope.launch {
            repository.clearSession(session.id)
        }
    }

    fun createNewSession(onCreated: (Session) -> Unit) {
        viewModelScope.launch {
            val session = repository.createSession()
            onCreated(session)
        }
    }
}
