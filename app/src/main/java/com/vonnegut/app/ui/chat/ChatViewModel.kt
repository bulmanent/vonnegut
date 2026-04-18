package com.vonnegut.app.ui.chat

import android.app.Application
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message as LiteRtMessage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vonnegut.app.VonnegutApplication
import com.vonnegut.app.data.db.entities.Message
import com.vonnegut.app.data.db.entities.Role
import com.vonnegut.app.data.db.entities.Session
import com.vonnegut.app.inference.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MessageUi(
    val id: Long,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isTypingIndicator: Boolean = false
)

sealed class ChatUiState {
    object NoModel : ChatUiState()
    object ModelLoading : ChatUiState()
    object Ready : ChatUiState()
    object Generating : ChatUiState()
    data class LoadError(val message: String) : ChatUiState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VonnegutApplication
    private val prefs = app.preferences
    private val repository = app.chatRepository
    private val inferenceEngine = app.inferenceEngine

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.NoModel)
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _messages = MutableStateFlow<List<MessageUi>>(emptyList())
    val messages: StateFlow<List<MessageUi>> = _messages

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession

    val recentSessions: StateFlow<List<Session>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var messagesJob: kotlinx.coroutines.Job? = null

    fun initialise() {
        // Called from onViewCreated and onResume — must be safe to call multiple times.
        // Skip if a load is already in progress.
        if (_uiState.value is ChatUiState.ModelLoading) return

        val modelPath = prefs.activeModelPath
        if (modelPath == null) {
            _uiState.value = ChatUiState.NoModel
            return
        }

        when (val engineState = inferenceEngine.state.value) {
            is InferenceEngine.State.Ready -> {
                // If the active model path changed (user swapped models), reload.
                if (engineState.modelPath != modelPath) {
                    loadModel(modelPath)
                } else {
                    _uiState.value = ChatUiState.Ready
                    ensureSession()
                }
            }
            is InferenceEngine.State.Loading,
            is InferenceEngine.State.Generating -> {
                _uiState.value = ChatUiState.ModelLoading
                waitForEngineReady()
            }
            is InferenceEngine.State.Idle -> {
                loadModel(modelPath)
            }
            is InferenceEngine.State.Error -> {
                // Retry on resume in case the user fixed the model file
                loadModel(modelPath)
            }
        }
    }

    private fun loadModel(modelPath: String) {
        viewModelScope.launch {
            _uiState.value = ChatUiState.ModelLoading
            try {
                inferenceEngine.load(
                    context = getApplication(),
                    modelPath = modelPath,
                    temperature = prefs.temperature
                )
                _uiState.value = ChatUiState.Ready
                ensureSession()
            } catch (e: Exception) {
                Log.e(TAG, "Model load failed", e)
                _uiState.value = ChatUiState.LoadError(e.message ?: "Unknown error loading model.")
            }
        }
    }

    private fun waitForEngineReady() {
        viewModelScope.launch {
            inferenceEngine.state
                .filter { it is InferenceEngine.State.Ready || it is InferenceEngine.State.Error }
                .first()
                .let { state ->
                    when (state) {
                        is InferenceEngine.State.Ready -> {
                            _uiState.value = ChatUiState.Ready
                            ensureSession()
                        }
                        is InferenceEngine.State.Error ->
                            _uiState.value = ChatUiState.LoadError(state.message)
                        else -> {}
                    }
                }
        }
    }

    private fun ensureSession() {
        viewModelScope.launch {
            val sessionId = prefs.currentSessionId
            val session = if (sessionId > 0) {
                repository.getSessionById(sessionId) ?: createNewSession()
            } else {
                repository.getMostRecentSession() ?: createNewSession()
            }
            switchToSession(session)
        }
    }

    private suspend fun createNewSession(): Session = repository.createSession()

    fun switchToSession(session: Session) {
        messagesJob?.cancel()
        _currentSession.value = session
        prefs.currentSessionId = session.id
        messagesJob = viewModelScope.launch {
            repository.getMessagesForSession(session.id).collect { dbMessages ->
                // Only replace the list when not mid-stream to avoid clobbering the typing indicator
                if (_uiState.value !is ChatUiState.Generating) {
                    _messages.value = dbMessages.map { m ->
                        MessageUi(m.id, m.role, m.content, m.timestamp)
                    }
                }
            }
        }
    }

    fun startNewSession() {
        viewModelScope.launch {
            val session = createNewSession()
            switchToSession(session)
        }
    }

    fun setActiveModelPath(modelPath: String) {
        if (prefs.activeModelPath == modelPath) return
        prefs.activeModelPath = modelPath
        viewModelScope.launch {
            inferenceEngine.release()
            initialise()
        }
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            val isCurrentSession = _currentSession.value?.id == session.id
            repository.deleteSession(session.id)
            if (isCurrentSession) startNewSession()
        }
    }

    fun renameCurrentSession(name: String) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            repository.renameSession(session.id, name)
            _currentSession.value = session.copy(name = name)
        }
    }

    fun sendMessage(text: String, attachments: List<ChatAttachment>) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        if (_uiState.value is ChatUiState.Generating) return
        if (_uiState.value !is ChatUiState.Ready) return

        val session = _currentSession.value ?: return

        viewModelScope.launch {
            val persistedUserContent = buildPersistedUserMessageContent(trimmed, attachments)
            // Persist user message
            repository.insertMessage(
                Message(sessionId = session.id, role = Role.USER, content = persistedUserContent)
            )

            val systemInstruction = buildSystemInstruction()
            val history = buildHistory(session.id)
            val currentTurnContents = buildCurrentTurnContents(trimmed, attachments)

            _uiState.value = ChatUiState.Generating
            showTypingIndicator()

            val accumulator = StringBuilder()

            try {
                inferenceEngine.generate(
                    systemInstruction = systemInstruction,
                    history = history,
                    userMessage = currentTurnContents,
                    temperature = prefs.temperature,
                    onToken = { token ->
                        accumulator.append(token)
                        updateStreamingMessage(accumulator.toString())
                    }
                )
                finaliseResponse(session.id, accumulator.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                removeTypingIndicator()
                _uiState.value = ChatUiState.Ready
                appendErrorMessage("Inference failed: ${e.message}")
            }
        }
    }

    private fun buildSystemInstruction(): String {
        val systemBlock = prefs.systemPrompt
        val profileBlock = prefs.buildUserProfileBlock()
        val instructionsBlock = prefs.customInstructions
        return buildString {
            appendLine(systemBlock)
            appendLine()
            appendLine(profileBlock)
            if (instructionsBlock.isNotBlank()) {
                appendLine()
                appendLine("[CUSTOM INSTRUCTIONS]")
                appendLine(instructionsBlock)
            }
        }.trim()
    }

    private suspend fun buildHistory(sessionId: Long): List<LiteRtMessage> =
        withContext(Dispatchers.Default) {
            val allMessages = repository.getMessagesSync(sessionId)
            // Drop the user message we just inserted (it's the last one)
            val history = allMessages.dropLast(1)
            // Apply a character budget so we don't pass unbounded context
            val budgetChars = prefs.contextWindowLimit * 4
            val result = mutableListOf<LiteRtMessage>()
            var spent = 0
            for (msg in history.asReversed()) {
                if (spent + msg.content.length > budgetChars) break
                result.add(
                    0,
                    if (msg.role == Role.USER) LiteRtMessage.user(msg.content) else LiteRtMessage.model(msg.content)
                )
                spent += msg.content.length
            }
            result
        }

    private fun buildCurrentTurnContents(
        text: String,
        attachments: List<ChatAttachment>
    ): Contents {
        val contents = mutableListOf<Content>()

        attachments.forEach { attachment ->
            when (attachment) {
                is ChatAttachment.Image ->
                    contents += Content.ImageFile(attachment.absolutePath)
                is ChatAttachment.TextDocument ->
                    contents += Content.Text(
                        buildString {
                            appendLine("[Attached document: ${attachment.label}]")
                            append(attachment.textContent)
                        }
                    )
            }
        }

        if (text.isNotBlank()) {
            contents += Content.Text(text)
        }

        return Contents.of(contents)
    }

    private fun buildPersistedUserMessageContent(
        text: String,
        attachments: List<ChatAttachment>
    ): String = buildString {
        attachments.forEach { attachment ->
            when (attachment) {
                is ChatAttachment.Image -> appendLine("[Image attached: ${attachment.label}]")
                is ChatAttachment.TextDocument -> appendLine("[Document attached: ${attachment.label}]")
            }
        }
        if (text.isNotBlank()) {
            if (isNotEmpty()) appendLine()
            append(text)
        }
    }.ifBlank { "[Attachment sent]" }

    private fun showTypingIndicator() {
        _messages.value = _messages.value + MessageUi(
            id = TYPING_INDICATOR_ID,
            role = Role.ASSISTANT,
            content = "",
            timestamp = System.currentTimeMillis(),
            isTypingIndicator = true
        )
    }

    private fun removeTypingIndicator() {
        _messages.value = _messages.value.filter { !it.isTypingIndicator }
    }

    private fun updateStreamingMessage(partial: String) {
        _messages.value = _messages.value.map { msg ->
            if (msg.isTypingIndicator) msg.copy(content = partial) else msg
        }
    }

    private suspend fun finaliseResponse(sessionId: Long, fullText: String) {
        val trimmed = fullText.trim()
        if (trimmed.isNotEmpty()) {
            repository.insertMessage(
                Message(sessionId = sessionId, role = Role.ASSISTANT, content = trimmed)
            )
        }
        removeTypingIndicator()
        _uiState.value = ChatUiState.Ready

        // Auto-prune
        val session = _currentSession.value ?: return
        val count = repository.getMessageCount(sessionId)
        if (count > session.pruneLimit) {
            repository.pruneSession(sessionId, session.pruneLimit)
        }
    }

    private fun appendErrorMessage(text: String) {
        _messages.value = _messages.value + MessageUi(
            id = ERROR_MESSAGE_ID,
            role = Role.ASSISTANT,
            content = "[Error: $text]",
            timestamp = System.currentTimeMillis()
        )
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val TYPING_INDICATOR_ID = -1L
        private const val ERROR_MESSAGE_ID = -2L
    }
}
