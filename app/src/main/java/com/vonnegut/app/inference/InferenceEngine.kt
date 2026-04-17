package com.vonnegut.app.inference

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.ErrorListener
import com.google.mediapipe.tasks.core.OutputHandler
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Wraps MediaPipe LlmInference.
 *
 * In tasks-genai 0.10.14 the result listener is registered once at construction time via
 * LlmInferenceOptions.setResultListener; generateResponseAsync(prompt) takes no callback.
 * We route the shared listener to per-call handlers stored in [currentCallbacks].
 *
 * Only one generation may be in flight at a time. ChatViewModel enforces this.
 */
class InferenceEngine {

    sealed class State {
        object Idle : State()
        object Loading : State()
        data class Ready(val modelPath: String) : State()
        object Generating : State()
        data class Error(val message: String) : State()
    }

    private data class Callbacks(
        val onToken: (String) -> Unit,
        val onDone: () -> Unit,
        val onError: (Exception) -> Unit
    )

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null

    // Swapped atomically before each generateResponseAsync call
    private val currentCallbacks = AtomicReference<Callbacks?>(null)

    val isReady: Boolean
        get() = _state.value is State.Ready

    /**
     * Load a model. The result and error listeners are wired into the options so they persist
     * for the lifetime of this LlmInference instance.
     * Runs on IO dispatcher; suspends until complete.
     */
    suspend fun load(
        context: Context,
        modelPath: String,
        maxTokens: Int,
        temperature: Float
    ) = withContext(Dispatchers.IO) {
        _state.value = State.Loading
        release()
        try {
            val resultListener = OutputHandler.ProgressListener<String> { partialResult, done ->
                val cbs = currentCallbacks.get() ?: return@ProgressListener
                if (partialResult != null && partialResult.isNotEmpty()) {
                    cbs.onToken(partialResult)
                }
                if (done) {
                    _state.value = State.Ready(loadedModelPath ?: modelPath)
                    currentCallbacks.set(null)
                    cbs.onDone()
                }
            }

            val errorListener = ErrorListener { e ->
                val cbs = currentCallbacks.getAndSet(null)
                _state.value = State.Ready(loadedModelPath ?: modelPath)
                if (cbs != null) cbs.onError(e) else Log.e(TAG, "Unhandled inference error", e)
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .setTemperature(temperature)
                .setTopK(40)
                .setRandomSeed(1)
                .setResultListener(resultListener)
                .setErrorListener(errorListener)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
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
     * Start async generation. Results stream back via [onToken]; [onDone] fires once complete.
     * Callbacks may arrive on a background thread — callers must marshal to Main as needed.
     */
    fun generateAsync(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val inference = llmInference
        val modelPath = loadedModelPath
        if (inference == null || modelPath == null || _state.value !is State.Ready) {
            onError(IllegalStateException("Model not loaded"))
            return
        }

        currentCallbacks.set(Callbacks(onToken, onDone, onError))
        _state.value = State.Generating

        try {
            inference.generateResponseAsync(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "generateResponseAsync threw: ${e.message}", e)
            currentCallbacks.set(null)
            _state.value = State.Ready(modelPath)
            onError(e)
        }
    }

    fun release() {
        currentCallbacks.set(null)
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LlmInference: ${e.message}")
        } finally {
            llmInference = null
            loadedModelPath = null
            _state.value = State.Idle
        }
    }

    companion object {
        private const val TAG = "InferenceEngine"
    }
}
