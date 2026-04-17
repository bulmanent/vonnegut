package com.vonnegut.app.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Wraps LiteRT-LM Engine for on-device inference with Gemma 4 (.litertlm) model files.
 *
 * Load the model once with [load]; call [generate] for each inference turn.
 * Only one generation may be in flight at a time — ChatViewModel enforces this.
 */
class InferenceEngine {

    sealed class State {
        object Idle : State()
        object Loading : State()
        data class Ready(val modelPath: String) : State()
        object Generating : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var engine: Engine? = null
    private var loadedModelPath: String? = null

    val isReady: Boolean get() = _state.value is State.Ready

    /**
     * Load a model from [modelPath]. Suspends on IO until the engine is initialised.
     * [context] is used for the GPU shader cache directory.
     */
    suspend fun load(
        context: Context,
        modelPath: String,
        temperature: Float
    ) = withContext(Dispatchers.IO) {
        _state.value = State.Loading
        release()
        try {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
            val newEngine = Engine(engineConfig)
            newEngine.initialize()
            engine = newEngine
            loadedModelPath = modelPath
            _state.value = State.Ready(modelPath)
            Log.i(TAG, "Model loaded: $modelPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}", e)
            loadedModelPath = null
            _state.value = State.Error("Failed to load model: ${e.message}")
            throw e
        }
    }

    /**
     * Run a single inference turn. Suspends until the full response is streamed.
     *
     * @param systemInstruction  Combined system prompt + user profile + custom instructions.
     * @param history            Prior turns as (role, content) pairs; role = "user" | "assistant".
     * @param userMessage        The new user message for this turn.
     * @param temperature        Sampling temperature.
     * @param onToken            Called on each streamed token (may arrive on a background thread).
     */
    suspend fun generate(
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        temperature: Float,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val eng = engine ?: throw IllegalStateException("Model not loaded")
        _state.value = State.Generating
        try {
            val historyMessages = history.map { (role, content) ->
                if (role == "user") Message.user(content) else Message.model(content)
            }
            val config = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                initialMessages = historyMessages,
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature.toDouble())
            )
            eng.createConversation(config).use { conversation ->
                conversation.sendMessageAsync(userMessage).collect { message ->
                    val text = message.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    if (text.isNotEmpty()) onToken(text)
                }
            }
            _state.value = State.Ready(loadedModelPath!!)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}", e)
            _state.value = State.Ready(loadedModelPath ?: "")
            throw e
        }
    }

    fun release() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Engine: ${e.message}")
        } finally {
            engine = null
            loadedModelPath = null
            _state.value = State.Idle
        }
    }

    companion object {
        private const val TAG = "InferenceEngine"
    }
}
