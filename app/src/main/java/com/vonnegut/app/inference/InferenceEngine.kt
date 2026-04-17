package com.vonnegut.app.inference

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Wraps MediaPipe LlmInference. One model is active at a time.
 *
 * generateAsync streams partial tokens via [onToken]; [onDone] fires when complete.
 * Callbacks may arrive on a background thread — callers must marshal to Main if needed.
 *
 * NOTE: LlmInference does not support concurrent requests. ChatViewModel serialises calls.
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

    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null

    val isReady: Boolean
        get() = _state.value is State.Ready

    /**
     * Load a model. Releases any previously loaded model first.
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
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .setTemperature(temperature)
                .setRandomSeed(1)
                .setTopK(40)
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
     * Generate a response asynchronously with token streaming.
     * [onToken] is called per partial token (may be on a background thread).
     * [onDone] and [onError] are called exactly once, on a background thread.
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

        _state.value = State.Generating

        try {
            inference.generateAsync(prompt) { partialResult, done ->
                if (partialResult != null) {
                    onToken(partialResult)
                }
                if (done) {
                    _state.value = State.Ready(modelPath)
                    onDone()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}", e)
            _state.value = State.Ready(modelPath)
            onError(e)
        }
    }

    fun release() {
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
